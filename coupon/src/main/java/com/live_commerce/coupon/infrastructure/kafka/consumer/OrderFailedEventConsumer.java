package com.live_commerce.coupon.infrastructure.kafka.consumer;

import com.live_commerce.coupon.application.service.IssuedCouponService;
import com.live_commerce.coupon.infrastructure.kafka.event.OrderFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 주문 실패 이벤트 Consumer
 * 주문 실패 시 사용된 쿠폰을 복구합니다.
 */
@Slf4j
@Component
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
