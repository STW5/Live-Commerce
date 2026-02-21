package com.live_commerce.livebroadcast.application.dto.result;

import com.live_commerce.livebroadcast.domain.model.BroadcastProduct;

import java.util.UUID;

public record BroadcastProductResult(
        UUID broadcastProductId,
        UUID liveBroadcastId,
        UUID productId
) {
    public static BroadcastProductResult from(BroadcastProduct bp) {
        return new BroadcastProductResult(
                bp.getBroadcastProductId(),
                bp.getLiveBroadcastId(),
                bp.getProductId()
        );
    }
}
