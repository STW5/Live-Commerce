package com.live_commerce.coupon.adapter.in.kafka;

import com.live_commerce.coupon.domain.port.in.RestoreCouponUseCase;
import com.live_commerce.events.order.OrderFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 주문 실패 이벤트 Kafka Consumer (Inbound Adapter)
 *
 * [헥사고날 아키텍처 적용]
 * - IssuedCouponService 직접 주입 → RestoreCouponUseCase 인터페이스로 대체
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFailedKafkaConsumer {

    private final RestoreCouponUseCase restoreCouponUseCase;

    @KafkaListener(
            topics = "order-failed",
            groupId = "${spring.application.name}-hexagonal"
    )
    public void onOrderFailed(OrderFailedEvent event) {
        log.info("❌ 주문 실패 이벤트 수신(kafka): orderId={}", event.orderId());
        try {
            restoreCouponUseCase.restoreCouponByOrderFailed(event.orderId());
            log.info("✅ 쿠폰 복구 완료 - orderId: {}", event.orderId());
        } catch (Exception e) {
            log.error("❌ 쿠폰 복구 실패 - orderId: {}, error: {}", event.orderId(), e.getMessage());
            throw e;
        }
    }
}
