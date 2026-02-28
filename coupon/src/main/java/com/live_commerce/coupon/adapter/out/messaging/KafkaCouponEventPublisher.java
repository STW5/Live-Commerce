package com.live_commerce.coupon.adapter.out.messaging;

import com.live_commerce.coupon.adapter.out.outbox.OutboxEventHelper;
import com.live_commerce.coupon.domain.port.out.CouponEventPublisher;
import com.live_commerce.coupon.infrastructure.kafka.event.CouponUsedEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 쿠폰 이벤트 발행 어댑터 (CouponEventPublisher 구현체)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaCouponEventPublisher implements CouponEventPublisher {

    private final OutboxEventHelper outboxEventHelper;

    @Override
    public void publishCouponUsedEvent(UUID couponId, UUID userId) {
        CouponUsedEvent event = new CouponUsedEvent(couponId, userId);
        outboxEventHelper.saveEvent("COUPON", couponId, "COUPON_USED", "coupon-used", event);
        log.info("[KafkaCouponEventPublisher] 쿠폰 사용 이벤트 Outbox 저장: couponId={}", couponId);
    }
}
