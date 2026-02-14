package com.live_commerce.order.application.service;

import com.live_commerce.order.application.dto.request.OrderUpdateRequest;
import com.live_commerce.order.application.dto.response.OrderUpdateResponse;
import com.live_commerce.order.application.exception.OrderException;
import com.live_commerce.order.application.exception.OrderExceptionCode;
import com.live_commerce.order.domain.model.DISCOUNT_TYPE;
import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.model.OrderStatus;
import com.live_commerce.order.domain.port.out.OrderRepositoryPort;
import com.live_commerce.order.infrastructure.client.feign.CouponClient;
import com.live_commerce.order.infrastructure.client.feign.ProductClient;
import com.live_commerce.order.infrastructure.client.response.*;
import com.live_commerce.order.presentation.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class OrderModificationService {

    private final OrderRepositoryPort orderRepositoryPort;
    private final ProductClient productClient;
    private final CouponClient couponClient;

    //주문 수정 service - 주문 상태 변경을 일어나지 않음.
    //주문 개수, 요청 사항, 쿠폰만 수정 가능
    @Transactional
    public OrderUpdateResponse updateCreator(UUID orderId, OrderUpdateRequest request, UUID userId, String role) {

        // orderId에 해당하는 주문 가져오기
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderExceptionCode.NOT_FOUND));
        UUID productId = order.getProductId();

        //고객일 경우 본인의 결제 내역만 수정 가능하게
        if ("ROLE_CUSTOMER".equals(role) && !order.getUserId().equals(userId)) {
            throw new OrderException("고객은 자신의 주문만 수정할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        // 현재 주문의 상태가 PENDING일때만 수정 가능!
        OrderStatus status = order.getStatus();
        if (status != OrderStatus.PENDING) {
            throw new OrderException("주문 내역을 수정할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        // 상품 정보 조회
        ApiResponse<ProductCreateResponseDto> responseProduct = productClient.getProduct(productId);
        ProductCreateResponseDto productResponseByOrder = responseProduct.getData();

        // 재고 확인 로직
        ApiResponse<InventoryCheckResponseDto> responseInventory = productClient.checkOrderableInventory(productId, request.productQuantity());
        InventoryCheckResponseDto checkInventory = responseInventory.getData();
        if (!checkInventory.orderAvailable()) {
            throw new OrderException("재고가 없는 상태입니다.", HttpStatus.BAD_REQUEST);
        }
        log.info("재고 존재 여부 확인 완료");

        // 상품이 품절일 경우
        if (productResponseByOrder == null) {
            throw new OrderException("해당 상품은 품절된 상품입니다.", HttpStatus.BAD_REQUEST);
        }

        // 해당 상품의 남은 재고 수량 들고오기
        ApiResponse<InventoryCheckQuantityResponseDto> responseInventoryByQuantity = productClient.checkInventoryQuantity(productId, request.productQuantity());
        InventoryCheckQuantityResponseDto getInventoryQuantity = responseInventoryByQuantity.getData();

        int orderQty = request.productQuantity();
        log.info("재고 수량 들고오기");

        // total 주문 금액 계산
        double productTotalPrice = (orderQty * productResponseByOrder.price());
        log.info("총 주문 금액 계산");

        // 최종 결제 금액 계산
        double finalPaidPrice = productTotalPrice;

        // 유저 쿠폰 목록 조회
        ApiResponse<IssuedCouponListResponse> responseCouponList = couponClient.getIssuedCoupons();
        IssuedCouponListResponse couponListByUser = responseCouponList.getData();

        // 쿠폰이 없으면 원가 결제
        if ((couponListByUser == null) || (couponListByUser.coupons() == null)) {
            order.applyUpdate(request.productQuantity(), productTotalPrice, finalPaidPrice,
                    request.requirement(), request.couponId());
            Order saved = orderRepositoryPort.save(order);
            return OrderUpdateResponse.fromOrder(saved);
        }

        // 요청 couponId 확인
        UUID requestCouponId = request.couponId();
        log.info("요청 couponId : " + requestCouponId);

        // 쿠폰Id가 없으면 원가 결제
        if (requestCouponId == null) {
            order.applyUpdate(request.productQuantity(), productTotalPrice, finalPaidPrice,
                    request.requirement(), null);
            Order saved = orderRepositoryPort.save(order);
            return OrderUpdateResponse.fromOrder(saved);
        }

        // 요청 쿠폰 목록에서 찾기
        GetIssuedCouponResponse matchedCoupon = couponListByUser.coupons().stream()
                .filter(coupon -> coupon.id().equals(requestCouponId))
                .findFirst()
                .orElseThrow(() -> new OrderException("요청하신 쿠폰은 사용자의 보유 목록에 없습니다.", HttpStatus.BAD_REQUEST));

        // couponCode로 정책 조회
        String requestCouponCode = matchedCoupon.couponCode();
        ApiResponse<ReadCouponPolicyResponse> responseCouponPolicy = couponClient.getCouponPolicy(requestCouponCode);
        ReadCouponPolicyResponse couponPolicyByCouponCode = responseCouponPolicy.getData();

        // 최종 결제 금액 계산 (할인 적용)
        DISCOUNT_TYPE discountType = couponPolicyByCouponCode.discountType();
        double discountValue = couponPolicyByCouponCode.discountValue();

        if (discountType == DISCOUNT_TYPE.FIXED) {
            finalPaidPrice = productTotalPrice - discountValue;
        }
        if (discountType == DISCOUNT_TYPE.RATE) {
            double discountAmount = (productTotalPrice * discountValue) / 100;
            finalPaidPrice = productTotalPrice - discountAmount;
        }
        log.info("할인 금액 적용한 최종 결제 예상 금액");

        // 주문 수정
        order.applyUpdate(request.productQuantity(), productTotalPrice, finalPaidPrice,
                request.requirement(), requestCouponId);
        Order saved = orderRepositoryPort.save(order);
        return OrderUpdateResponse.fromOrder(saved);
    }
}
