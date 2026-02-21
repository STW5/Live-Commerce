package com.live_commerce.livebroadcast.domain.port.in;

import com.live_commerce.livebroadcast.application.dto.result.BroadcastSubscriptionResult;

import java.util.UUID;

public interface SubscribeBroadcastUseCase {
    BroadcastSubscriptionResult subscribe(UUID userId, UUID broadcastId);
}
