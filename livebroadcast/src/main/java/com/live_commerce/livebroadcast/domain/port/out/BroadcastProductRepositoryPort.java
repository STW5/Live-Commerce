package com.live_commerce.livebroadcast.domain.port.out;

import com.live_commerce.livebroadcast.domain.model.BroadcastProduct;

import java.util.Optional;
import java.util.UUID;

public interface BroadcastProductRepositoryPort {
    BroadcastProduct save(BroadcastProduct broadcastProduct);
    Optional<BroadcastProduct> findByBroadcastIdAndProductId(UUID broadcastId, UUID productId);
    boolean existsByBroadcastIdAndProductId(UUID broadcastId, UUID productId);
    void softDelete(UUID broadcastProductId, UUID deletedById);
}
