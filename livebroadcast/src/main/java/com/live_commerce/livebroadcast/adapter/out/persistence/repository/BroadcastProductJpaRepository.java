package com.live_commerce.livebroadcast.adapter.out.persistence.repository;

import com.live_commerce.livebroadcast.adapter.out.persistence.entity.BroadcastProductJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BroadcastProductJpaRepository extends JpaRepository<BroadcastProductJpaEntity, UUID> {

    boolean existsByLiveBroadcastIdAndProductIdAndDeletedStatusFalse(UUID liveBroadcastId, UUID productId);

    Optional<BroadcastProductJpaEntity> findByLiveBroadcastIdAndProductIdAndDeletedStatusFalse(UUID liveBroadcastId, UUID productId);
}
