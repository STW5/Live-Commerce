package com.live_commerce.order.adapter.out.client;

import com.live_commerce.order.application.dto.result.PaymentReadyResult;
import com.live_commerce.order.domain.port.out.PaymentPort;
import com.live_commerce.order.infrastructure.PaymentReadyResponseDto;
import com.live_commerce.order.infrastructure.client.feign.PaymentClient;
import com.live_commerce.order.infrastructure.client.request.PaymentReadyRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * PaymentPort 구현체 (Feign Client Adapter)
 * - Application/Domain Layer가 PaymentClient에 직접 의존하지 않도록 격리
 * - 결제 준비: readyPayment → KakaoPay tid + redirectUrl 반환
 * - 결제 환불: refundPayment → 환불 처리 (응답 무시)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFeignAdapter implements PaymentPort {

    private final PaymentClient paymentClient;

    @Override
    public PaymentReadyResult readyPayment(UUID orderId, BigDecimal amount, String productId) {
        PaymentReadyRequestDto request = new PaymentReadyRequestDto(orderId, amount, productId);
        PaymentReadyResponseDto dto = paymentClient.readyPayment(request).getData();
        return new PaymentReadyResult(dto.tid(), dto.nextRedirectUrl());
    }

    @Override
    public void refundPayment(UUID orderId) {
        paymentClient.refundPayment(orderId);
        log.info("[PaymentFeignAdapter] 결제 환불 요청 완료 - orderId: {}", orderId);
    }
}
