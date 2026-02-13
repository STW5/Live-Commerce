package com.live_commerce.order.kafkaOrder.service;


import com.live_commerce.common.saga.SagaState;
import com.live_commerce.common.saga.SagaStateRepository;
import com.live_commerce.order.application.dto.request.OrderCreateRequest;
import com.live_commerce.order.application.dto.response.OrderCreateResponse;
import com.live_commerce.order.application.exception.OrderException;
import com.live_commerce.order.domain.model.DISCOUNT_TYPE;
import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.repository.OrderRepository;
import com.live_commerce.order.infrastructure.client.feign.BroadcastClient;
import com.live_commerce.order.infrastructure.client.feign.CouponClient;
import com.live_commerce.order.infrastructure.client.feign.ProductClient;
import com.live_commerce.order.infrastructure.client.feignEnum.BroadcastStatus;
import com.live_commerce.order.infrastructure.client.request.ProductOrderPriceDto;
import com.live_commerce.order.infrastructure.client.response.*;
import com.live_commerce.order.presentation.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;


//주문 생성 service
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCreateServiceKafka {
    private final BroadcastClient broadcastClient;
    private final ProductClient productClient;
    private final OrderRepository orderRepository;
    private final CouponClient couponClient;
    private final SagaStateRepository sagaStateRepository;

    //주문 생성 함수
    @Transactional
    public OrderCreateResponse orderCreator(OrderCreateRequest request, UUID userId) {

        // 0. 방송중인지 확인 - 방송중일때만 주문 가능
        ApiResponse<BroadcastStatusResponse> response = broadcastClient.getBroadcast(request.broadcastId());
        BroadcastStatusResponse statusResponse = response.getData();
        if (statusResponse == null || statusResponse.getBroadcastStatus() != BroadcastStatus.LIVE) {
            throw new OrderException("방송 중일 때만 주문이 가능합니다.", HttpStatus.BAD_REQUEST);
        }
        log.info("방송 체크 완료");

        // 1. [productClient] 상품 쪽으로 검증 요청 필요 (상품의 개수랑 상품 ID를 같이 넘김)
        // 재고가 없거나 상품이 없다면 Exception
        // order -> product (productId, productQuantity)
        ApiResponse<ProductCreateResponseDto> responseProduct = productClient.getProduct(request.productId()); //주문 요청 상품 id -> product
        ProductCreateResponseDto productResponseByOrder = responseProduct.getData();

        log.info("상품 정보 들고오기" + productResponseByOrder);

        // 1.2 실시간 상품 가격 조회
        ApiResponse<ProductOrderPriceDto> requestProductPrice = productClient.getPriceForOrder(request.productId());
        ProductOrderPriceDto productOrderPriceDto = requestProductPrice.getData();
        int unitPrice = productOrderPriceDto.currentPrice();

        // productId에 해당하는 상품이 아예 없는 경우
        if (productResponseByOrder == null) {
            throw new OrderException("해당 상품이 존재하지 않습니다.", HttpStatus.BAD_REQUEST);
        }
        log.info("상품 검증 완료");

        // 2. 재고가 주문보다 많나 체크 - 주문이 현재 가능한 상태인지 확인 로직
        ApiResponse<InventoryCheckResponseDto> responseInventory = productClient.checkOrderableInventory(request.productId(), request.orderQuantity());
        InventoryCheckResponseDto checkInventory = responseInventory.getData();
        if (!checkInventory.orderAvailable()) {
            throw new OrderException("재고가 없는 상태입니다.", HttpStatus.BAD_REQUEST);
        }
        log.info("재고 존재 여부 확인 완료");

        int orderQty = request.orderQuantity(); // 사용자가 주문한 상품의 수량 (요청 order -> product)
        log.info("주문 수량 들고오기 : " + orderQty);

        // 5. total 주문 금액 계산
        // 주문한 수량 * 주문한 상품 한 개의 가격
        log.info("상품의 가격 : " + productResponseByOrder.price());
        //double productTotalPrice = orderQty * productResponseByOrder.price();
        double productTotalPrice = orderQty * unitPrice;
        log.info("총 상품 주문 금액 계산 : " + productTotalPrice);

        ///////////////////////////////////////////////////////////////////////////////

        // 6.  쿠폰에서 할인 적용할 금액 계산하도록 쿠폰아이디와 총 주문 금액(쿠폰 적용전) 넘겨주기
        // 최종 결제 금액 필드 초기화
        double finalPaidPrice = productTotalPrice;

        // 6-1. 지금 로그인 한 유저에 대한 쿠폰 목록 리스트들을 전부 들고온다.
        ApiResponse<IssuedCouponListResponse> responseCouponList = couponClient.getIssuedCoupons();
        IssuedCouponListResponse couponListByUser = responseCouponList.getData();

        // 6-2. 쿠폰이 하나도 없다면 쿠폰 적용 없이 즉시 최종 결제 금액으로 반환 -> 원가로 결제
        if( (couponListByUser == null) || (couponListByUser.coupons() == null)){
            Order order = request.toOrder(productTotalPrice, finalPaidPrice, userId);
            Order savedOrder = orderRepository.save(order);
            createSagaState(savedOrder.getId());
            return OrderCreateResponse.of(savedOrder);
        }
        log.info("로그인 한 유저의 쿠폰 리스트 들고오기");

        // 6-3. 유저 목록 쿠폰이 있다면 -> 요청에서 들어온 couponId확인
        // 쿠폰id가 요청으로 들어오고 로그인한 사람의 쿠폰 목록들이 있다면 쿠폰 목록에서 요청 들어온 쿠폰 id 조회

        // 요청 couponId
        UUID requestCouponId = request.couponId();
        log.info("요청한 쿠폰 아이디" + requestCouponId);

        // 만약 요청 들어온 couponId가 없으면 원가 결제
        if (requestCouponId == null) {
            Order order = request.toOrder(productTotalPrice, finalPaidPrice, userId);
            Order savedOrder = orderRepository.save(order);
            createSagaState(savedOrder.getId());
            return OrderCreateResponse.of(savedOrder);
        }

        // 요청 들어온 couponId를 목록에서 찾고 그 일치하는 쿠폰 정보로 변수에 할당
        // 만약 요청 들어온 couponId가 쿠폰 목록에 없다면 해당 쿠폰이 목록에 없다는 예외 발생
        GetIssuedCouponResponse matchedCoupon = couponListByUser.coupons().stream()
                .filter(coupon -> coupon.id().equals(requestCouponId))
                .findFirst()
                .orElseThrow(() -> new OrderException("요청하신 쿠폰은 사용자의 보유 목록에 없습니다.", HttpStatus.BAD_REQUEST));
        log.info("요청에 맞는 쿠폰아이디를 목록에서 찾아서 쿠폰 정보 들고오기");

        // 7. 그 쿠폰에 해당하는 couponCode 찾기 (order -> coupon)
        // couponCode 가져오기
        String requestCouponCode = matchedCoupon.couponCode();
        log.info("해당 쿠폰의 couponCode 들고오기" + requestCouponCode);

        // 7-1. couponCode로 coupon policy에서 쿠폰 정책 조회 및 가져오기
        // TODO ReadCouponPolicyResponse의 type -> enum으로 변경
        ApiResponse<ReadCouponPolicyResponse> responseCouponPolicy= couponClient.getCouponPolicy(requestCouponCode);
        ReadCouponPolicyResponse couponPolicyByCouponCode = responseCouponPolicy.getData();
        log.info("쿠폰 정책 조회 및 들고오기");

        //8. 최종 결제 금액 계산
        //할인 타입 - fixed, rate
        DISCOUNT_TYPE discountType = couponPolicyByCouponCode.discountType();
        log.info("쿠폰 할인 타입" + discountType);
        // 할인률, 할인값
        double discountValue = couponPolicyByCouponCode.discountValue();
        log.info("할인률 혹은 할인값 : " + discountValue);

        //할인값으로 계산
        if(discountType == DISCOUNT_TYPE.FIXED){
            finalPaidPrice = productTotalPrice - discountValue;  //할인 고정값
        }

        //할인률로 계산
        if(discountType == DISCOUNT_TYPE.RATE){
            double discountAmount = (productTotalPrice * discountValue) / 100;  //할인률로 계산
            finalPaidPrice = productTotalPrice - discountAmount;
        }
        log.info("할인 금액 적용한 최종 결제 예상 금액" + finalPaidPrice);

        // 7. 주문 생성 - 전체 물건 합과 userId는 따로 받아와야함
        Order order = request.toOrder(productTotalPrice, finalPaidPrice, userId);
        log.info("주문 생성 : 저장전");

        // 8. 생성 주문 저장 - 주문 생성 후, 상품 상태 PENDING
        Order savedOrder = orderRepository.save(order);
        log.info("주문 생성 저장 완료!!!!!");
        createSagaState(savedOrder.getId());
        return OrderCreateResponse.of(savedOrder);
    }

    /**
     * 주문 생성 시 Saga 상태 초기화
     */
    private void createSagaState(UUID orderId) {
        try {
            SagaState sagaState = SagaState.start("ORDER_CREATION", orderId, null);
            sagaStateRepository.save(sagaState);
            log.info("[Saga] 상태 초기화 완료 - orderId: {}", orderId);
        } catch (Exception e) {
            // Saga 저장 실패는 주문 플로우에 영향 없음 (보조 기능)
            log.warn("[Saga] 상태 초기화 실패 (무시) - orderId: {}, error: {}", orderId, e.getMessage());
        }
    }
}