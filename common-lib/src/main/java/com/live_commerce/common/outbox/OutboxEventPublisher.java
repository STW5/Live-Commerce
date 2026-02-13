package com.live_commerce.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Outbox 이벤트를 Kafka로 발행하는 Publisher
 * - 5초마다 PENDING 상태 이벤트 조회
 * - 동기 방식으로 Kafka 발행 (결과 확인 후 상태 업데이트)
 * - 성공 시 PUBLISHED, 실패 시 FAILED + retryCount 증가
 * - 1분마다 재시도 가능한 FAILED 이벤트 PENDING으로 복귀
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 5초마다 PENDING 이벤트 발행
     */
    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents =
                outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("[Outbox] 발행 대기 중인 이벤트 {}건 처리 시작", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            publishEvent(event);
        }
    }

    /**
     * 동기 방식으로 Kafka 발행 후 상태 업데이트
     * - kafkaTemplate.send().get()으로 결과를 기다림
     * - 트랜잭션 독립 처리 (이벤트별 성공/실패 분리)
     */
    private void publishEvent(OutboxEvent event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getAggregateId().toString(), event.getPayload())
                    .get(5, TimeUnit.SECONDS);
            updateEventStatus(event.getId(), true, null);
            log.info("[Outbox] 발행 성공 - topic: {}, aggregateId: {}, eventType: {}",
                    event.getTopic(), event.getAggregateId(), event.getEventType());
        } catch (Exception e) {
            updateEventStatus(event.getId(), false, e.getMessage());
            log.error("[Outbox] 발행 실패 - topic: {}, aggregateId: {}, error: {}",
                    event.getTopic(), event.getAggregateId(), e.getMessage());
        }
    }

    /**
     * 이벤트 상태 업데이트 (별도 트랜잭션)
     */
    @Transactional
    public void updateEventStatus(UUID eventId, boolean success, String errorMessage) {
        OutboxEvent event = outboxEventRepository.findById(eventId).orElseThrow();
        if (success) {
            event.markPublished();
        } else {
            event.markFailed(errorMessage);
        }
        outboxEventRepository.save(event);
    }

    /**
     * 1분마다 재시도 가능한 FAILED 이벤트를 PENDING으로 복귀
     */
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void retryFailedEvents() {
        List<OutboxEvent> failedEvents = outboxEventRepository.findRetryableFailedEvents();

        if (failedEvents.isEmpty()) {
            return;
        }

        log.info("[Outbox] 재시도 가능한 실패 이벤트 {}건 PENDING 복귀", failedEvents.size());

        for (OutboxEvent event : failedEvents) {
            if (event.canRetry()) {
                event.resetForRetry();
                outboxEventRepository.save(event);
                log.info("[Outbox] 재시도 예약 - eventId: {}, retryCount: {}",
                        event.getId(), event.getRetryCount());
            }
        }
    }

    /**
     * 매일 자정 7일 이상 지난 PUBLISHED 이벤트 정리
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanUpOldEvents() {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        outboxEventRepository.deleteByStatusAndPublishedAtBefore(OutboxStatus.PUBLISHED, sevenDaysAgo);
        log.info("[Outbox] 7일 이상 지난 PUBLISHED 이벤트 정리 완료");
    }
}
