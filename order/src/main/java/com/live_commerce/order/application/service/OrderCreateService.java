package com.live_commerce.order.application.service;

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
import com.live_commerce.order.infrastructure.client.response.*;
import com.live_commerce.order.presentation.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 주문 생성 서비스 (레거시)
 *
 * @deprecated 핵사고날 아키텍처 전환으로 {@link CreateOrderService} (CreateOrderUseCase 구현체)로 대체 예정.
 * 현재는 기존 OrderService에서 참조하므로 유지. 향후 OrderService가 CreateOrderUseCase를 주입하도록 변경 후 삭제.
 */
@Deprecated(since = "hexagonal-ddd-pilot", forRemoval = true)
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCreateService {
    private final BroadcastClient broadcastClient;
    private final ProductClient productClient;
    private final OrderRepositoryPort orderRepositoryPort;
    private final CouponClient couponClient;

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

        if (productResponseByOrder == null) {
            throw new OrderException("해당 상품이 존재하지 않습니다.", HttpStatus.BAD_REQUEST);
        }
        log.info("상품 조회 완료");

        ApiResponse<InventoryCheckResponseDto> responseInventory = productClient.checkOrderableInventory(request.productId(), request.orderQuantity());
        InventoryCheckResponseDto checkInventory = responseInventory.getData();
        if (!checkInventory.orderAvailable()) {
            throw new OrderException("재고가 없는 상태입니다.", HttpStatus.BAD_REQUEST);
        }
        log.info("재고 존재 여부 확인 완료");

        int orderQty = request.orderQuantity();
        double productTotalPrice = orderQty * productResponseByOrder.price();
        double finalPaidPrice = productTotalPrice;

        ApiResponse<IssuedCouponListResponse> responseCouponList = couponClient.getIssuedCoupons();
        IssuedCouponListResponse couponListByUser = responseCouponList.getData();

        if ((couponListByUser == null) || (couponListByUser.coupons() == null)) {
            Order order = buildOrder(userId, request, orderQty, productTotalPrice, finalPaidPrice);
            Order savedOrder = orderRepositoryPort.save(order);
            return OrderCreateResponse.of(savedOrder);
        }

        UUID requestCouponId = request.couponId();
        if (requestCouponId == null) {
            Order order = buildOrder(userId, request, orderQty, productTotalPrice, finalPaidPrice);
            Order savedOrder = orderRepositoryPort.save(order);
            return OrderCreateResponse.of(savedOrder);
        }

        GetIssuedCouponResponse matchedCoupon = couponListByUser.coupons().stream()
                .filter(coupon -> coupon.id().equals(requestCouponId))
                .findFirst()
                .orElseThrow(() -> new OrderException("요청하신 쿠폰은 사용자의 보유 목록에 없습니다.", HttpStatus.BAD_REQUEST));

        String requestCouponCode = matchedCoupon.couponCode();
        ApiResponse<ReadCouponPolicyResponse> responseCouponPolicy = couponClient.getCouponPolicy(requestCouponCode);
        ReadCouponPolicyResponse couponPolicyByCouponCode = responseCouponPolicy.getData();

        DISCOUNT_TYPE discountType = couponPolicyByCouponCode.discountType();
        double discountValue = couponPolicyByCouponCode.discountValue();

        if (discountType == DISCOUNT_TYPE.FIXED) {
            finalPaidPrice = productTotalPrice - discountValue;
        }
        if (discountType == DISCOUNT_TYPE.RATE) {
            double discountAmount = (productTotalPrice * discountValue) / 100;
            finalPaidPrice = productTotalPrice - discountAmount;
        }

        Order order = buildOrder(userId, request, orderQty, productTotalPrice, finalPaidPrice);
        Order savedOrder = orderRepositoryPort.save(order);
        return OrderCreateResponse.of(savedOrder);
    }

    /**
     * Order 도메인 객체 생성 (레거시용)
     * Order.reconstitute() 를 사용해 빌더 없이 Order 생성
     */
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
}
