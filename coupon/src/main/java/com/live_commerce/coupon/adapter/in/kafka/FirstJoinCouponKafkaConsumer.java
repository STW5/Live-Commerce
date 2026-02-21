package com.live_commerce.coupon.adapter.in.kafka;

import com.live_commerce.coupon.domain.port.in.IssueCouponUseCase;
import com.live_commerce.coupon.infrastructure.kafka.event.FirstJoinCouponEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 회원가입 쿠폰 발급 Kafka Consumer (Inbound Adapter)
 *
 * [헥사고날 아키텍처 적용]
 * - IssuedCouponService 직접 주입 → IssueCouponUseCase 인터페이스로 대체
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FirstJoinCouponKafkaConsumer {

    private final IssueCouponUseCase issueCouponUseCase;

    @KafkaListener(
            topics = "first-join-coupon",
            groupId = "${spring.application.name}"
    )
    public void onFirstJoin(FirstJoinCouponEvent msg) {
        issueCouponUseCase.issueFirstJoinCoupon(msg.userId());
        log.info("✅ 회원가입 쿠폰이 정상 발급되었습니다.(kafka): userId={}", msg.userId());
    }
}
