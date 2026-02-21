package com.live_commerce.order.adapter.out.messaging;

import java.util.UUID;

/**
 * 쿠폰 사용 이벤트 (Kafka 전송용)
 * - Topic: coupon-used
 * - key: userId
 */
public record CouponUsedEvent(
        UUID couponId,
        UUID userId
) {}
