package com.live_commerce.order.domain.port.in;

import java.util.UUID;

/**
 * 결제 실패 처리 유즈케이스 (Inbound Port)
 * - 구현체: application/service/HandlePaymentFailureService
 */
public interface HandlePaymentFailureUseCase {
    void handle(UUID orderId, String failureMessage);
}
