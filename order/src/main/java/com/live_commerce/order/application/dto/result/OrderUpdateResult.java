package com.live_commerce.order.application.dto.result;

import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.model.OrderStatus;

import java.util.UUID;

/**
 * 주문 수정 결과
 */
public record OrderUpdateResult(
        UUID orderId,
        UUID productId,
        int productQuantity,
        double productTotalPrice,
        String requirement,
        OrderStatus status
) {
    public static OrderUpdateResult from(Order order) {
        return new OrderUpdateResult(
                order.getId(),
                order.getProductId(),
                order.getQuantity().value(),
                order.getTotalPrice().toDouble(),
                order.getRequirement(),
                order.getStatus()
        );
    }
}
