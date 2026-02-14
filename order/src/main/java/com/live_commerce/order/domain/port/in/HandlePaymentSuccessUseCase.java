package com.live_commerce.order.domain.port.in;

import java.util.UUID;

/**
 * 결제 성공 처리 유즈케이스 (Inbound Port)
 * - 구현체: application/service/HandlePaymentSuccessService
 */
public interface HandlePaymentSuccessUseCase {
    void handle(UUID orderId, String message);
}
