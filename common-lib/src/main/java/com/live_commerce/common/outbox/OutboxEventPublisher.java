package com.live_commerce.common.outbox;

import com.live_commerce.common.notification.SlackNotificationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
 * - Prometheus 메트릭: outbox.pending.count, outbox.published.total, outbox.failed.total,
 *                      outbox.retry.total, outbox.dead.total
 * - Slack 알림: Dead Letter 발생 시 CRITICAL, PENDING > 30 시 WARNING
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private static final int PENDING_WARNING_THRESHOLD  = 30;
    private static final int PENDING_CRITICAL_THRESHOLD = 100;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Autowired(required = false)
    private SlackNotificationService slackNotificationService;

    private Counter publishedCounter;
    private Counter failedCounter;
    private Counter retryCounter;
    private Counter deadCounter;

    @PostConstruct
    void initMetrics() {
        publishedCounter = Counter.builder("outbox.published.total")
            .description("Outbox 발행 성공 누적")
            .register(meterRegistry);

        failedCounter = Counter.builder("outbox.failed.total")
            .description("Outbox 발행 실패 누적")
            .register(meterRegistry);

        retryCounter = Counter.builder("outbox.retry.total")
            .description("Outbox 재시도 누적")
            .register(meterRegistry);

        deadCounter = Counter.builder("outbox.dead.total")
            .description("Outbox Dead Letter 누적 (재시도 불가)")
            .register(meterRegistry);

        Gauge.builder("outbox.pending.count", outboxEventRepository,
                repo -> repo.countByStatus(OutboxStatus.PENDING))
            .description("현재 PENDING 이벤트 수")
            .register(meterRegistry);
    }

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

        // 임계치 초과 알림
        if (pendingEvents.size() > PENDING_CRITICAL_THRESHOLD) {
            alertSlack("CRITICAL", pendingEvents.size());
        } else if (pendingEvents.size() > PENDING_WARNING_THRESHOLD) {
            alertSlack("WARNING", pendingEvents.size());
        }

        for (OutboxEvent event : pendingEvents) {
            publishEvent(event);
        }
    }

    /**
     * 동기 방식으로 Kafka 발행 후 상태 업데이트
     */
    private void publishEvent(OutboxEvent event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getAggregateId().toString(), event.getPayload())
                    .get(5, TimeUnit.SECONDS);
            updateEventStatus(event.getId(), true, null);
            publishedCounter.increment();
            log.info("[Outbox] 발행 성공 - topic: {}, aggregateId: {}, eventType: {}",
                    event.getTopic(), event.getAggregateId(), event.getEventType());
        } catch (Exception e) {
            updateEventStatus(event.getId(), false, e.getMessage());
            failedCounter.increment();
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
     * retryCount >= 3인 이벤트는 Dead Letter로 처리하고 Slack 알림
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
                retryCounter.increment();
                log.info("[Outbox] 재시도 예약 - eventId: {}, retryCount: {}",
                        event.getId(), event.getRetryCount());
            } else {
                deadCounter.increment();
                sendDeadLetterAlert(event);
                log.error("[Outbox] Dead Letter 이벤트 - eventId: {}, topic: {}, aggregateId: {}, error: {}",
                        event.getId(), event.getTopic(), event.getAggregateId(), event.getErrorMessage());
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

    private void sendDeadLetterAlert(OutboxEvent event) {
        if (slackNotificationService == null) return;
        try {
            String title = "🚨 [CRITICAL] Outbox Dead Letter 이벤트 발생";
            String message = String.format(
                """
                aggregateType: %s
                aggregateId: %s
                topic: %s
                eventType: %s
                retryCount: %d
                error: %s

                ⚠️ 수동 개입 필요: POST /api/v1/admin/outbox/retry
                """,
                event.getAggregateType(),
                event.getAggregateId(),
                event.getTopic(),
                event.getEventType(),
                event.getRetryCount(),
                event.getErrorMessage() != null ? event.getErrorMessage() : "N/A"
            );
            slackNotificationService.sendCriticalAlert(title, message);
        } catch (Exception e) {
            log.error("[Outbox] Dead Letter Slack 알림 실패 - eventId: {}, error: {}",
                    event.getId(), e.getMessage());
        }
    }

    private void alertSlack(String level, int count) {
        if (slackNotificationService == null) return;
        try {
            if ("CRITICAL".equals(level)) {
                slackNotificationService.sendCriticalAlert(
                    "🚨 Outbox 처리 지연 심각",
                    "PENDING 이벤트가 " + count + "건 쌓였습니다.\nKafka 연결 또는 처리 지연을 즉시 확인하세요."
                );
            } else {
                slackNotificationService.sendWarningAlert(
                    "⚠️ Outbox 처리 지연 감지",
                    "PENDING 이벤트가 " + count + "건입니다.\n모니터링을 권장합니다."
                );
            }
        } catch (Exception e) {
            log.error("[Outbox] PENDING 임계치 Slack 알림 실패 - error: {}", e.getMessage());
        }
    }
}
