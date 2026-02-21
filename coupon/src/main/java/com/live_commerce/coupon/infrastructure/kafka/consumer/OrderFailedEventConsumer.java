package com.live_commerce.coupon.infrastructure.kafka.consumer;

import com.live_commerce.coupon.application.service.IssuedCouponService;
import com.live_commerce.events.order.OrderFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 주문 실패 이벤트 Consumer
 * 주문 실패 시 사용된 쿠폰을 복구합니다.
 */
/**
 * @deprecated 헥사고날 아키텍처 전환으로 {@code adapter.in.kafka.OrderFailedKafkaConsumer}로 대체됨.
 *             중복 처리 방지를 위해 @Component 비활성화.
 */
@Deprecated(since = "hexagonal-ddd-coupon", forRemoval = true)
@Slf4j
// @Component  // 비활성화: OrderFailedKafkaConsumer (adapter.in.kafka) 로 대체
@RequiredArgsConstructor
public class OrderFailedEventConsumer {

    private final IssuedCouponService issuedCouponService;

    @KafkaListener(
            topics = "order-failed",
            groupId = "${spring.application.name}"
    )
    public void onOrderFailed(OrderFailedEvent event) {
        log.info("❌ 주문 실패 이벤트 수신(kafka): orderId={}, message={}",
                event.orderId(), event.message());

        try {
            issuedCouponService.handleOrderFailedEvent(event.orderId());
            log.info("✅ 쿠폰 복구 완료 - orderId: {}", event.orderId());
        } catch (Exception e) {
            log.error("❌ 쿠폰 복구 실패 - orderId: {}, error: {}",
                    event.orderId(), e.getMessage());
            throw e;
        }
    }
}
