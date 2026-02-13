package com.live_commerce.events.payment;

import java.util.UUID;

/**
 * 결제 실패 이벤트 (Payment → Order)
 * Producer: payment-service
 * Consumer: order-service
 * Topic: payment-failed
 */
public record PaymentFailedEvent(
    UUID orderId,
    String message
) {
    public static PaymentFailedEvent of(UUID orderId, String message) {
        return new PaymentFailedEvent(orderId, message);
    }
}
