package com.live_commerce.livebroadcast.domain.port.out;

import com.live_commerce.livebroadcast.application.dto.result.LiveBroadcastResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface BroadcastQueryPort {
    Page<LiveBroadcastResult> searchByName(String keyword, Pageable pageable);
    Page<UUID> findProductIdsByBroadcastId(UUID broadcastId, Pageable pageable);
}
