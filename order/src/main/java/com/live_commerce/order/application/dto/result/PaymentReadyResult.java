package com.live_commerce.order.application.dto.result;

/**
 * 결제 준비 결과 (KakaoPay tid + 리다이렉트 URL)
 */
public record PaymentReadyResult(
        String tid,
        String nextRedirectUrl
) {}
