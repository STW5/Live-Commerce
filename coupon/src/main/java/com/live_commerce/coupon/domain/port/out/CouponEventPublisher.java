package com.live_commerce.coupon.domain.port.out;

import java.util.UUID;

/**
 * 쿠폰 이벤트 발행 포트 (Outbound)
 * - 구현체: adapter/out/messaging/KafkaCouponEventPublisher
 */
public interface CouponEventPublisher {
    void publishCouponUsedEvent(UUID couponId, UUID userId);
}
