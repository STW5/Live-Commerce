package com.live_commerce.livebroadcast.domain.port.in;

import java.util.UUID;

public interface DisconnectBroadcastProductUseCase {
    void disconnectProduct(UUID broadcastId, UUID productId, UUID userId, String role);
}
