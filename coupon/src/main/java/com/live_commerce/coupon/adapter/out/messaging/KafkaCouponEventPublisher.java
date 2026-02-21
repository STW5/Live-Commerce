package com.live_commerce.coupon.adapter.out.messaging;

import com.live_commerce.coupon.domain.port.out.CouponEventPublisher;
import com.live_commerce.coupon.infrastructure.kafka.event.CouponUsedEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 쿠폰 이벤트 발행 어댑터 (CouponEventPublisher 구현체)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaCouponEventPublisher implements CouponEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishCouponUsedEvent(UUID couponId, UUID userId) {
        CouponUsedEvent event = new CouponUsedEvent(couponId, userId);
        kafkaTemplate.send("coupon-used", event);
        log.info("[KafkaCouponEventPublisher] 쿠폰 사용 이벤트 발행: couponId={}, userId={}", couponId, userId);
    }
}
