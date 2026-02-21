package com.live_commerce.livebroadcast.domain.port.in;

import java.util.UUID;

public interface UnsubscribeBroadcastUseCase {
    void unsubscribe(UUID userId, UUID broadcastId);
}
