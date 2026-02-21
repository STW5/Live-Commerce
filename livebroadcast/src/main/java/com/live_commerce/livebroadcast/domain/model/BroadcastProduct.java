package com.live_commerce.livebroadcast.domain.model;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class BroadcastProduct extends BaseEntity {

    private UUID broadcastProductId;
    private UUID liveBroadcastId;
    private UUID productId;

    private BroadcastProduct() {}

    public static BroadcastProduct create(UUID liveBroadcastId, UUID productId) {
        BroadcastProduct bp = new BroadcastProduct();
        bp.broadcastProductId = UUID.randomUUID();
        bp.liveBroadcastId = liveBroadcastId;
        bp.productId = productId;
        return bp;
    }

    public static BroadcastProduct reconstitute(UUID id, UUID liveBroadcastId, UUID productId,
            LocalDateTime createdAt, String createdBy, LocalDateTime updatedAt, String updatedBy,
            LocalDateTime deletedAt, UUID deletedBy, boolean deletedStatus) {
        BroadcastProduct bp = new BroadcastProduct();
        bp.broadcastProductId = id;
        bp.liveBroadcastId = liveBroadcastId;
        bp.productId = productId;
        bp.setAuditFields(createdAt, createdBy, updatedAt, updatedBy, deletedAt, deletedBy, deletedStatus);
        return bp;
    }
}
