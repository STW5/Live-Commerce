package com.live_commerce.order.application.dto.result;

import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.model.OrderStatus;

import java.util.UUID;

/**
 * 주문 단건 조회 결과
 */
public record OrderGetResult(
        UUID orderId,
        UUID userId,
        UUID productId,
        UUID broadcastId,
        UUID couponId,
        int productQuantity,
        double productTotalPrice,
        double finalPaidPrice,
        String requirement,
        OrderStatus status
) {
    public static OrderGetResult from(Order order) {
        return new OrderGetResult(
                order.getId(),
                order.getUserId(),
                order.getProductId(),
                order.getBroadcastId(),
                order.getCouponId(),
                order.getProductQuantity(),
                order.getProductTotalPrice(),
                order.getFinalPaidPrice(),
                order.getRequirement(),
                order.getStatus()
        );
    }
}
