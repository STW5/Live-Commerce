package com.live_commerce.order.application.service;

import com.live_commerce.order.application.dto.command.UpdateOrderCommand;
import com.live_commerce.order.application.dto.result.OrderUpdateResult;
import com.live_commerce.order.application.dto.result.ProductInfo;
import com.live_commerce.order.application.exception.OrderException;
import com.live_commerce.order.application.exception.OrderExceptionCode;
import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.model.OrderStatus;
import com.live_commerce.order.domain.model.vo.DiscountPolicy;
import com.live_commerce.order.domain.model.vo.Money;
import com.live_commerce.order.domain.port.in.UpdateOrderUseCase;
import com.live_commerce.order.domain.port.out.CouponQueryPort;
import com.live_commerce.order.domain.port.out.OrderRepositoryPort;
import com.live_commerce.order.domain.port.out.ProductQueryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 주문 수정 서비스 (Hexagonal - UpdateOrderUseCase 구현체)
 * - PENDING 상태 주문만 수정 가능
 * - ProductQueryPort, CouponQueryPort 사용 (Feign 직접 의존 제거)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateOrderService implements UpdateOrderUseCase {

    private final OrderRepositoryPort orderRepositoryPort;
    private final ProductQueryPort productQueryPort;
    private final CouponQueryPort couponQueryPort;

    @Override
    @Transactional
    public OrderUpdateResult updateOrder(UUID orderId, UpdateOrderCommand command, UUID userId, String role) {
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderExceptionCode.NOT_FOUND));

        if ("ROLE_CUSTOMER".equals(role) && !order.getUserId().equals(userId)) {
            throw new OrderException("고객은 자신의 주문만 수정할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OrderException("주문 내역을 수정할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        int quantity = command.productQuantity() != null ? command.productQuantity() : order.getQuantity().value();

        // 상품 정보 및 재고 확인
        ProductInfo product = productQueryPort.findById(order.getProductId());
        if (product == null) {
            throw new OrderException("해당 상품은 품절된 상품입니다.", HttpStatus.BAD_REQUEST);
        }
        if (!productQueryPort.isOrderable(order.getProductId(), quantity)) {
            throw new OrderException("재고가 없는 상태입니다.", HttpStatus.BAD_REQUEST);
        }
        log.info("[UpdateOrderService] 재고 확인 완료 - productId: {}", order.getProductId());

        // 금액 계산
        Money totalPrice = product.unitPrice().multiply(quantity);
        Money finalPrice = totalPrice;

        // 쿠폰 할인 적용
        UUID couponId = command.couponId();
        if (couponId != null) {
            if (!couponQueryPort.userHasCoupon(userId, couponId)) {
                throw new OrderException("요청하신 쿠폰은 사용자의 보유 목록에 없습니다.", HttpStatus.BAD_REQUEST);
            }
            String couponCode = couponQueryPort.getCouponCode(userId, couponId);
            DiscountPolicy discountPolicy = couponQueryPort.getDiscountPolicy(couponCode);
            finalPrice = discountPolicy.apply(totalPrice);
            log.info("[UpdateOrderService] 쿠폰 할인 적용 - couponId: {}", couponId);
        }

        order.applyUpdate(quantity, totalPrice.toDouble(), finalPrice.toDouble(),
                command.requirement(), couponId);

        Order saved = orderRepositoryPort.save(order);
        log.info("[UpdateOrderService] 주문 수정 완료 - orderId: {}", orderId);
        return OrderUpdateResult.from(saved);
    }
}
