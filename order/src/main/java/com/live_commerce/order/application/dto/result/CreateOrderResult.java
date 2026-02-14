package com.live_commerce.order.application.dto.result;

import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.model.OrderStatus;

import java.util.UUID;

/**
 * 주문 생성 결과 (Application → Presentation)
 */
public record CreateOrderResult(
        UUID orderId,
        OrderStatus status,
        double totalPrice,
        double finalPrice
) {
    public static CreateOrderResult from(Order order) {
        return new CreateOrderResult(
                order.getId(),
                order.getStatus(),
                order.getProductTotalPrice(),
                order.getFinalPaidPrice()
        );
    }
}
