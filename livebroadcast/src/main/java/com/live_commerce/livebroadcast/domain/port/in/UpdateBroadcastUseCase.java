package com.live_commerce.livebroadcast.domain.port.in;

import com.live_commerce.livebroadcast.application.dto.command.UpdateBroadcastCommand;
import com.live_commerce.livebroadcast.application.dto.result.LiveBroadcastResult;

import java.util.UUID;

public interface UpdateBroadcastUseCase {
    LiveBroadcastResult updateBroadcast(UUID broadcastId, UpdateBroadcastCommand command, UUID userId, String role);
}
