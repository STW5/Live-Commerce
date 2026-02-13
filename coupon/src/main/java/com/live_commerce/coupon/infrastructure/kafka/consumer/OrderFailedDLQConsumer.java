package com.live_commerce.coupon.infrastructure.kafka.consumer;

import com.live_commerce.events.order.OrderFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 쿠폰 복구 최종 실패 DLQ Consumer
 * - order-failed.DLQ 토픽 소비
 * - 관리자 알림 및 수동 조치 유도
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFailedDLQConsumer {

    @KafkaListener(
            topics = "order-failed.DLQ",
            groupId = "${spring.application.name}-dlq"
    )
    public void onOrderFailedDLQ(
            @Payload OrderFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        log.error("""
                ======================================================
                [DLQ] 쿠폰 복구 최종 실패
                ======================================================
                Topic  : {}
                Offset : {}
                OrderId: {}
                Message: {}
                ======================================================
                ⚠️ 수동 쿠폰 복구가 필요합니다.
                ======================================================
                """, topic, offset, event.orderId(), event.message());

        // TODO: Slack 알림 발송 (NotificationService Feign 연동 시 추가)
        // notificationClient.sendCriticalAlert("쿠폰 복구 최종 실패", event.orderId().toString());

        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }
}
