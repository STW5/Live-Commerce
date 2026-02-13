package com.live_commerce.events.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 결제 완료 이벤트 (Payment → Order)
 * Producer: payment-service
 * Consumer: order-service
 * Topic: payment-completed
 */
public record PaymentCompletedEvent(
    UUID orderId,
    String message,
    BigDecimal finalPaidPrice
) {
    public static PaymentCompletedEvent of(UUID orderId, String message, BigDecimal finalPaidPrice) {
        return new PaymentCompletedEvent(orderId, message, finalPaidPrice);
    }
}
