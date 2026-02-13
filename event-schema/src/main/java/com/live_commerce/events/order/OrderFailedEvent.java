package com.live_commerce.events.order;

import java.util.UUID;

/**
 * 주문 실패 이벤트 (Order → Payment, Coupon)
 * Producer: order-service
 * Consumer: payment-service, coupon-service
 * Topic: order-failed
 */
public record OrderFailedEvent(
    UUID orderId,
    String message
) {
    public static OrderFailedEvent of(UUID orderId, String message) {
        return new OrderFailedEvent(orderId, message);
    }
}
