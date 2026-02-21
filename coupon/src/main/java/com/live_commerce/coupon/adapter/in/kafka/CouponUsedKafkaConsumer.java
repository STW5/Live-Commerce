package com.live_commerce.coupon.adapter.in.kafka;

import com.live_commerce.coupon.application.dto.command.UseCouponCommand;
import com.live_commerce.coupon.domain.port.in.UseCouponUseCase;
import com.live_commerce.coupon.infrastructure.kafka.event.CouponUsedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 쿠폰 사용 이벤트 Kafka Consumer (Inbound Adapter)
 *
 * [헥사고날 아키텍처 적용]
 * - IssuedCouponService 직접 주입 → UseCouponUseCase 인터페이스로 대체
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponUsedKafkaConsumer {

    private final UseCouponUseCase useCouponUseCase;

    @KafkaListener(
            topics = "coupon-used",
            groupId = "${spring.application.name}-hexagonal"
    )
    public void onCouponUsed(CouponUsedEvent msg) {
        useCouponUseCase.useCoupon(new UseCouponCommand(msg.couponId(), msg.userId()));
        log.info("✅ 쿠폰 사용 이벤트 처리 완료(kafka): couponId={}, userId={}", msg.couponId(), msg.userId());
    }
}
