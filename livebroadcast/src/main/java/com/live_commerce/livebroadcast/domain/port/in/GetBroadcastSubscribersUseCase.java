package com.live_commerce.livebroadcast.domain.port.in;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface GetBroadcastSubscribersUseCase {
    Page<UUID> getSubscribers(UUID broadcastId, Pageable pageable);
}
