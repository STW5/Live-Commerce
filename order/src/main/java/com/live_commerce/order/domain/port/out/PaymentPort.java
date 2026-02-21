package com.live_commerce.order.domain.port.out;

import com.live_commerce.order.application.dto.result.PaymentReadyResult;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 결제 처리 Port (Outbound)
 * - Feign 기반 동기 호출
 * - 구현체: adapter/out/client/PaymentFeignAdapter
 */
public interface PaymentPort {
    /**
     * 결제 준비 요청 (KakaoPay ready)
     * @return tid + nextRedirectUrl
     */
    PaymentReadyResult readyPayment(UUID orderId, BigDecimal amount, String productId);

    /**
     * 결제 환불/취소 요청 (PAID → REFUNDED)
     */
    void refundPayment(UUID orderId);
}
