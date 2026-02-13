package com.live_commerce.common.outbox;

/**
 * Outbox 이벤트 상태
 */
public enum OutboxStatus {
    PENDING,    // 발행 대기 중
    PUBLISHED,  // Kafka 발행 완료
    FAILED      // 발행 실패 (재시도 예정)
}
