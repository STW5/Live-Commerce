package com.live_commerce.livebroadcast.domain.port.out;

import com.live_commerce.livebroadcast.domain.model.BroadcastStatus;
import com.live_commerce.livebroadcast.domain.model.LiveBroadcast;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LiveBroadcastRepositoryPort {
    LiveBroadcast save(LiveBroadcast broadcast);
    Optional<LiveBroadcast> findById(UUID broadcastId);
    boolean existsById(UUID broadcastId);
    List<LiveBroadcast> findAllByStatusIn(List<BroadcastStatus> statuses);
    UUID findHostIdByBroadcastId(UUID broadcastId);
    void softDelete(UUID broadcastId, UUID deletedById);
}
