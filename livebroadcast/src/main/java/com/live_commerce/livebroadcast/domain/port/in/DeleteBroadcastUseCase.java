package com.live_commerce.livebroadcast.domain.port.in;

import java.util.UUID;

public interface DeleteBroadcastUseCase {
    void deleteBroadcast(UUID broadcastId, UUID userId, String role);
}
