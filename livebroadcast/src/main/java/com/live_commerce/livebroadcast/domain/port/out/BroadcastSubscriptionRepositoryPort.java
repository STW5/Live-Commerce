package com.live_commerce.livebroadcast.domain.port.out;

import com.live_commerce.livebroadcast.domain.model.BroadcastSubscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BroadcastSubscriptionRepositoryPort {
    BroadcastSubscription save(BroadcastSubscription subscription);
    Optional<BroadcastSubscription> findByUserIdAndBroadcastId(UUID userId, UUID broadcastId);
    boolean existsByUserIdAndBroadcastId(UUID userId, UUID broadcastId);
    List<BroadcastSubscription> findAllByUserId(UUID userId);
    Page<UUID> findSubscriberIdsByBroadcastId(UUID broadcastId, Pageable pageable);
    void softDelete(UUID subscriptionId, UUID deletedById);
}
