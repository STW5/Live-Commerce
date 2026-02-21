package com.live_commerce.livebroadcast.adapter.out.persistence.repository;

import com.live_commerce.livebroadcast.adapter.out.persistence.entity.LiveBroadcastJpaEntity;
import com.live_commerce.livebroadcast.domain.model.BroadcastStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LiveBroadcastJpaRepository extends JpaRepository<LiveBroadcastJpaEntity, UUID> {

    Optional<LiveBroadcastJpaEntity> findByLiveBroadcastIdAndDeletedStatusFalse(UUID id);

    boolean existsByLiveBroadcastIdAndDeletedStatusFalse(UUID broadcastId);

    List<LiveBroadcastJpaEntity> findAllByDeletedStatusFalseAndBroadcastStatusIn(List<BroadcastStatus> statuses);

    @Query("SELECT b.hostId FROM LiveBroadcastJpaEntity b WHERE b.liveBroadcastId = :id AND b.deletedStatus = false")
    UUID findHostIdByBroadcastId(@Param("id") UUID id);
}
