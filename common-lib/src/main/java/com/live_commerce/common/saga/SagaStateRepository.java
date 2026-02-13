package com.live_commerce.common.saga;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Saga 상태 리포지토리
 */
public interface SagaStateRepository extends JpaRepository<SagaState, UUID> {

    /**
     * Aggregate ID로 Saga 조회
     */
    Optional<SagaState> findByAggregateId(UUID aggregateId);

    /**
     * 실행 중인 Saga 조회
     */
    List<SagaState> findByStatus(SagaStatus status);

    /**
     * 보상이 필요한 실패 Saga 조회
     */
    List<SagaState> findByStatusAndCurrentStepNot(SagaStatus status, String currentStep);
}
