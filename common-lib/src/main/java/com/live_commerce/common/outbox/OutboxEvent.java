package com.live_commerce.common.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outbox Pattern을 위한 이벤트 저장소
 * - DB 트랜잭션과 함께 이벤트 저장
 * - 별도 Publisher가 주기적으로 Kafka로 발행
 */
@Entity
@Table(name = "outbox_event", indexes = {
    @Index(name = "idx_outbox_status", columnList = "status"),
    @Index(name = "idx_outbox_created_at", columnList = "createdAt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String aggregateType;  // "Order", "Payment" 등

    @Column(nullable = false)
    private UUID aggregateId;  // orderId, paymentId 등

    @Column(nullable = false)
    private String eventType;  // "OrderCreated", "PaymentCompleted" 등

    @Column(nullable = false)
    private String topic;  // Kafka topic

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;  // JSON 직렬화된 이벤트 데이터

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;  // PENDING, PUBLISHED, FAILED

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime publishedAt;

    private int retryCount;

    private String errorMessage;

    // 정적 팩토리 메서드
    public static OutboxEvent create(
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String topic,
        String payload
    ) {
        OutboxEvent event = new OutboxEvent();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.topic = topic;
        event.payload = payload;
        event.status = OutboxStatus.PENDING;
        event.createdAt = LocalDateTime.now();
        event.retryCount = 0;
        return event;
    }

    // 비즈니스 메서드
    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = OutboxStatus.FAILED;
        this.errorMessage = errorMessage;
        this.retryCount++;
    }

    public void resetForRetry() {
        if (this.retryCount < 3) {  // 최대 3번 재시도
            this.status = OutboxStatus.PENDING;
            this.errorMessage = null;
        }
    }

    public boolean canRetry() {
        return this.retryCount < 3 && this.status == OutboxStatus.FAILED;
    }
}
