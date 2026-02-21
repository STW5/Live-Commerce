package com.live_commerce.livebroadcast.domain.port.in;

import java.util.UUID;

public interface CheckBroadcastProductExistsUseCase {
    boolean exists(UUID broadcastId, UUID productId);
}
