package com.live_commerce.common.saga;

/**
 * Saga 실행 상태
 */
public enum SagaStatus {
    STARTED,        // Saga 시작됨
    RUNNING,        // Saga 실행 중
    COMPLETED,      // Saga 정상 완료
    FAILED,         // Saga 실패
    COMPENSATING,   // 보상 트랜잭션 실행 중
    COMPENSATED     // 보상 트랜잭션 완료
}
