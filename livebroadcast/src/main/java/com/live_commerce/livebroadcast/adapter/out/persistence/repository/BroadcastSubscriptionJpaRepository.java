package com.live_commerce.livebroadcast.adapter.out.persistence.repository;

import com.live_commerce.livebroadcast.adapter.out.persistence.entity.BroadcastSubscriptionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BroadcastSubscriptionJpaRepository extends JpaRepository<BroadcastSubscriptionJpaEntity, UUID> {

    boolean existsByUserIdAndBroadcastIdAndDeletedStatusFalse(UUID userId, UUID broadcastId);

    List<BroadcastSubscriptionJpaEntity> findAllByUserIdAndDeletedStatusFalse(UUID userId);

    Optional<BroadcastSubscriptionJpaEntity> findByUserIdAndBroadcastIdAndDeletedStatusFalse(UUID userId, UUID broadcastId);

    @Query("SELECT s.userId FROM BroadcastSubscriptionJpaEntity s WHERE s.broadcastId = :broadcastId AND s.deletedStatus = false")
    Page<UUID> findSubscriberIdsByBroadcastId(@Param("broadcastId") UUID broadcastId, Pageable pageable);
}
