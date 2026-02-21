package com.live_commerce.livebroadcast.domain.model;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class BroadcastSubscription extends BaseEntity {

    private UUID subscriptionId;
    private UUID userId;
    private UUID broadcastId;

    private BroadcastSubscription() {}

    public static BroadcastSubscription create(UUID userId, UUID broadcastId) {
        BroadcastSubscription sub = new BroadcastSubscription();
        sub.subscriptionId = UUID.randomUUID();
        sub.userId = userId;
        sub.broadcastId = broadcastId;
        return sub;
    }

    public static BroadcastSubscription reconstitute(UUID id, UUID userId, UUID broadcastId,
            LocalDateTime createdAt, String createdBy, LocalDateTime updatedAt, String updatedBy,
            LocalDateTime deletedAt, UUID deletedBy, boolean deletedStatus) {
        BroadcastSubscription sub = new BroadcastSubscription();
        sub.subscriptionId = id;
        sub.userId = userId;
        sub.broadcastId = broadcastId;
        sub.setAuditFields(createdAt, createdBy, updatedAt, updatedBy, deletedAt, deletedBy, deletedStatus);
        return sub;
    }
}
