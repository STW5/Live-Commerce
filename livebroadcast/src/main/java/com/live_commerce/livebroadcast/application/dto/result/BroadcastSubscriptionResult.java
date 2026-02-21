package com.live_commerce.livebroadcast.application.dto.result;

import com.live_commerce.livebroadcast.domain.model.BroadcastSubscription;

import java.util.UUID;

public record BroadcastSubscriptionResult(
        UUID subscriptionId,
        UUID userId,
        UUID broadcastId
) {
    public static BroadcastSubscriptionResult from(BroadcastSubscription sub) {
        return new BroadcastSubscriptionResult(
                sub.getSubscriptionId(),
                sub.getUserId(),
                sub.getBroadcastId()
        );
    }
}
