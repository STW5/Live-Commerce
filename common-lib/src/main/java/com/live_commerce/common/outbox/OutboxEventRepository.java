package com.live_commerce.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Outbox 이벤트 리포지토리
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * 발행 대기 중인 이벤트 조회 (오래된 순)
     */
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status);

    /**
     * 재시도 가능한 실패 이벤트 조회
     */
    @Query("SELECT o FROM OutboxEvent o WHERE o.status = 'FAILED' AND o.retryCount < 5 ORDER BY o.createdAt ASC")
    List<OutboxEvent> findRetryableFailedEvents();

    /**
     * 특정 시간 이전의 성공 이벤트 삭제 (Clean up)
     */
    void deleteByStatusAndPublishedAtBefore(OutboxStatus status, LocalDateTime before);
}
