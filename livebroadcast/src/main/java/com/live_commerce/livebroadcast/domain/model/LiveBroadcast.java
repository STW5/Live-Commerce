package com.live_commerce.livebroadcast.domain.model;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class LiveBroadcast extends BaseEntity {

    private UUID liveBroadcastId;
    private String broadcastName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BroadcastStatus broadcastStatus;
    private UUID hostId;
    private UUID companyId;
    private Integer totalViewerCount;

    private LiveBroadcast() {}

    public static LiveBroadcast create(String broadcastName, LocalDateTime startTime,
            LocalDateTime endTime, UUID hostId, UUID companyId) {
        LiveBroadcast b = new LiveBroadcast();
        b.liveBroadcastId = UUID.randomUUID();
        b.broadcastName = broadcastName;
        b.startTime = startTime;
        b.endTime = endTime;
        b.broadcastStatus = BroadcastStatus.SCHEDULED;
        b.hostId = hostId;
        b.companyId = companyId;
        b.totalViewerCount = 0;
        return b;
    }

    public static LiveBroadcast reconstitute(UUID id, String broadcastName, LocalDateTime startTime,
            LocalDateTime endTime, BroadcastStatus status, UUID hostId, UUID companyId,
            Integer totalViewerCount, LocalDateTime createdAt, String createdBy,
            LocalDateTime updatedAt, String updatedBy, LocalDateTime deletedAt,
            UUID deletedBy, boolean deletedStatus) {
        LiveBroadcast b = new LiveBroadcast();
        b.liveBroadcastId = id;
        b.broadcastName = broadcastName;
        b.startTime = startTime;
        b.endTime = endTime;
        b.broadcastStatus = status;
        b.hostId = hostId;
        b.companyId = companyId;
        b.totalViewerCount = totalViewerCount;
        b.setAuditFields(createdAt, createdBy, updatedAt, updatedBy, deletedAt, deletedBy, deletedStatus);
        return b;
    }

    public void update(String broadcastName, LocalDateTime startTime, LocalDateTime endTime,
            BroadcastStatus broadcastStatus) {
        if (broadcastName != null) this.broadcastName = broadcastName;
        if (startTime != null) this.startTime = startTime;
        if (endTime != null) this.endTime = endTime;
        if (broadcastStatus != null) this.broadcastStatus = broadcastStatus;
    }

    public void updateStatus(BroadcastStatus newStatus) {
        this.broadcastStatus = newStatus;
    }
}
