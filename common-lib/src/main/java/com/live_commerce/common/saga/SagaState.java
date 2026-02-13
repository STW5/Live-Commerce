package com.live_commerce.common.saga;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Saga 상태 관리 Entity
 * - 분산 트랜잭션의 진행 상태 추적
 * - 실패 시 보상 트랜잭션 실행 지점 파악
 */
@Entity
@Table(name = "saga_state", indexes = {
    @Index(name = "idx_saga_aggregate_id", columnList = "aggregateId"),
    @Index(name = "idx_saga_status", columnList = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SagaState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID sagaId;

    @Column(nullable = false)
    private String sagaType;  // "OrderCreation", "PaymentRefund" 등

    @Column(nullable = false)
    private UUID aggregateId;  // orderId, paymentId 등

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStatus status;  // STARTED, RUNNING, COMPLETED, FAILED, COMPENSATING, COMPENSATED

    @Column(nullable = false)
    private String currentStep;  // "INVENTORY_DECREASE", "PAYMENT_READY" 등

    @Column(columnDefinition = "TEXT")
    private String payload;  // Saga 실행에 필요한 데이터 (JSON)

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private String errorMessage;

    private int retryCount;

    // 정적 팩토리 메서드
    public static SagaState start(String sagaType, UUID aggregateId, String payload) {
        SagaState saga = new SagaState();
        saga.sagaType = sagaType;
        saga.aggregateId = aggregateId;
        saga.status = SagaStatus.STARTED;
        saga.currentStep = "INIT";
        saga.payload = payload;
        saga.startedAt = LocalDateTime.now();
        saga.retryCount = 0;
        return saga;
    }

    // 비즈니스 메서드
    public void updateStep(String step) {
        this.currentStep = step;
        this.status = SagaStatus.RUNNING;
    }

    public void complete() {
        this.status = SagaStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String errorMessage) {
        this.status = SagaStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    public void startCompensation() {
        this.status = SagaStatus.COMPENSATING;
    }

    public void completeCompensation() {
        this.status = SagaStatus.COMPENSATED;
        this.completedAt = LocalDateTime.now();
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public boolean canRetry() {
        return this.retryCount < 3;
    }

    public boolean isInProgress() {
        return this.status == SagaStatus.STARTED || this.status == SagaStatus.RUNNING;
    }

    public boolean needsCompensation() {
        return this.status == SagaStatus.FAILED && !this.currentStep.equals("INIT");
    }
}
