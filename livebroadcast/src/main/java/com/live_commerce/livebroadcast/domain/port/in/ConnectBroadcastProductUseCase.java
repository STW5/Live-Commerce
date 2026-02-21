package com.live_commerce.livebroadcast.domain.port.in;

import com.live_commerce.livebroadcast.application.dto.result.BroadcastProductResult;

import java.util.UUID;

public interface ConnectBroadcastProductUseCase {
    BroadcastProductResult connectProduct(UUID broadcastId, UUID productId, UUID userId, String role);
}
