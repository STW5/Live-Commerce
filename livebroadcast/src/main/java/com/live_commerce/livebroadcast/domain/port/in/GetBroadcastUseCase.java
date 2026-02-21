package com.live_commerce.livebroadcast.domain.port.in;

import com.live_commerce.livebroadcast.application.dto.result.LiveBroadcastResult;

import java.util.UUID;

public interface GetBroadcastUseCase {
    LiveBroadcastResult getBroadcast(UUID broadcastId);
}
