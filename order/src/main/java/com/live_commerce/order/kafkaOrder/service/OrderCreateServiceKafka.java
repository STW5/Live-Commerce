package com.live_commerce.order.kafkaOrder.service;


import com.live_commerce.common.saga.SagaState;
import com.live_commerce.common.saga.SagaStateRepository;
import com.live_commerce.order.application.dto.request.OrderCreateRequest;
import com.live_commerce.order.application.dto.response.OrderCreateResponse;
import com.live_commerce.order.application.exception.OrderException;
import com.live_commerce.order.domain.model.DISCOUNT_TYPE;
import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.model.OrderStatus;
import com.live_commerce.order.domain.model.vo.Money;
import com.live_commerce.order.domain.model.vo.OrderQuantity;
import com.live_commerce.order.domain.port.out.OrderRepositoryPort;
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

import java.time.LocalDateTime;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCreateServiceKafka {
    private final BroadcastClient broadcastClient;
    private final ProductClient productClient;
    private final OrderRepositoryPort orderRepositoryPort;
    private final CouponClient couponClient;
    private final SagaStateRepository sagaStateRepository;

    @Transactional
    public OrderCreateResponse orderCreator(OrderCreateRequest request, UUID userId) {

        ApiResponse<BroadcastStatusResponse> response = broadcastClient.getBroadcast(request.broadcastId());
        BroadcastStatusResponse statusResponse = response.getData();
        if (statusResponse == null || statusResponse.getBroadcastStatus() != BroadcastStatus.LIVE) {
            throw new OrderException("방송 중일 때만 주문이 가능합니다.", HttpStatus.BAD_REQUEST);
        }
        log.info("방송 체크 완료");

        ApiResponse<ProductCreateResponseDto> responseProduct = productClient.getProduct(request.productId());
        ProductCreateResponseDto productResponseByOrder = responseProduct.getData();
        log.info("상품 정보 들고오기" + productResponseByOrder);

        ApiResponse<ProductOrderPriceDto> requestProductPrice = productClient.getPriceForOrder(request.productId());
        ProductOrderPriceDto productOrderPriceDto = requestProductPrice.getData();
        int unitPrice = productOrderPriceDto.currentPrice();

        if (productResponseByOrder == null) {
            throw new OrderException("해당 상품이 존재하지 않습니다.", HttpStatus.BAD_REQUEST);
        }
        log.info("상품 검증 완료");

        ApiResponse<InventoryCheckResponseDto> responseInventory = productClient.checkOrderableInventory(request.productId(), request.orderQuantity());
        InventoryCheckResponseDto checkInventory = responseInventory.getData();
        if (!checkInventory.orderAvailable()) {
            throw new OrderException("재고가 없는 상태입니다.", HttpStatus.BAD_REQUEST);
        }
        log.info("재고 존재 여부 확인 완료");

        int orderQty = request.orderQuantity();
        log.info("주문 수량 들고오기 : " + orderQty);

        double productTotalPrice = orderQty * unitPrice;
        log.info("총 상품 주문 금액 계산 : " + productTotalPrice);

        double finalPaidPrice = productTotalPrice;

        ApiResponse<IssuedCouponListResponse> responseCouponList = couponClient.getIssuedCoupons();
        IssuedCouponListResponse couponListByUser = responseCouponList.getData();

        if ((couponListByUser == null) || (couponListByUser.coupons() == null)) {
            Order order = buildOrder(userId, request, orderQty, productTotalPrice, finalPaidPrice);
            Order savedOrder = orderRepositoryPort.save(order);
            createSagaState(savedOrder.getId());
            return OrderCreateResponse.of(savedOrder);
        }
        log.info("로그인 한 유저의 쿠폰 리스트 들고오기");

        UUID requestCouponId = request.couponId();
        log.info("요청한 쿠폰 아이디" + requestCouponId);

        if (requestCouponId == null) {
            Order order = buildOrder(userId, request, orderQty, productTotalPrice, finalPaidPrice);
            Order savedOrder = orderRepositoryPort.save(order);
            createSagaState(savedOrder.getId());
            return OrderCreateResponse.of(savedOrder);
        }

        GetIssuedCouponResponse matchedCoupon = couponListByUser.coupons().stream()
                .filter(coupon -> coupon.id().equals(requestCouponId))
                .findFirst()
                .orElseThrow(() -> new OrderException("요청하신 쿠폰은 사용자의 보유 목록에 없습니다.", HttpStatus.BAD_REQUEST));
        log.info("요청에 맞는 쿠폰아이디를 목록에서 찾아서 쿠폰 정보 들고오기");

        String requestCouponCode = matchedCoupon.couponCode();
        log.info("해당 쿠폰의 couponCode 들고오기" + requestCouponCode);

        ApiResponse<ReadCouponPolicyResponse> responseCouponPolicy = couponClient.getCouponPolicy(requestCouponCode);
        ReadCouponPolicyResponse couponPolicyByCouponCode = responseCouponPolicy.getData();
        log.info("쿠폰 정책 조회 및 들고오기");

        DISCOUNT_TYPE discountType = couponPolicyByCouponCode.discountType();
        log.info("쿠폰 할인 타입" + discountType);
        double discountValue = couponPolicyByCouponCode.discountValue();
        log.info("할인률 혹은 할인값 : " + discountValue);

        if (discountType == DISCOUNT_TYPE.FIXED) {
            finalPaidPrice = productTotalPrice - discountValue;
        }
        if (discountType == DISCOUNT_TYPE.RATE) {
            double discountAmount = (productTotalPrice * discountValue) / 100;
            finalPaidPrice = productTotalPrice - discountAmount;
        }
        log.info("할인 금액 적용한 최종 결제 예상 금액" + finalPaidPrice);

        Order order = buildOrder(userId, request, orderQty, productTotalPrice, finalPaidPrice);
        log.info("주문 생성 : 저장전");

        Order savedOrder = orderRepositoryPort.save(order);
        log.info("주문 생성 저장 완료!!!!!");
        createSagaState(savedOrder.getId());
        return OrderCreateResponse.of(savedOrder);
    }

    private Order buildOrder(UUID userId, OrderCreateRequest request, int orderQty,
                              double productTotalPrice, double finalPaidPrice) {
        return Order.reconstitute(
                null,
                userId,
                request.productId(),
                request.broadcastId(),
                request.couponId(),
                new OrderQuantity(orderQty),
                Money.of(productTotalPrice),
                Money.of(finalPaidPrice),
                request.requirement(),
                OrderStatus.PENDING,
                LocalDateTime.now()
        );
    }

    private void createSagaState(UUID orderId) {
        try {
            SagaState sagaState = SagaState.start("ORDER_CREATION", orderId, null);
            sagaStateRepository.save(sagaState);
            log.info("[Saga] 상태 초기화 완료 - orderId: {}", orderId);
        } catch (Exception e) {
            log.warn("[Saga] 상태 초기화 실패 (무시) - orderId: {}, error: {}", orderId, e.getMessage());
        }
    }
}
