package com.live_commerce.livebroadcast.application.dto.result;

import com.live_commerce.livebroadcast.domain.model.BroadcastStatus;
import com.live_commerce.livebroadcast.domain.model.LiveBroadcast;

import java.time.LocalDateTime;
import java.util.UUID;

public record LiveBroadcastResult(
        UUID liveBroadcastId,
        String broadcastName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BroadcastStatus broadcastStatus,
        UUID hostId,
        UUID companyId,
        Integer totalViewerCount
) {
    public static LiveBroadcastResult from(LiveBroadcast broadcast) {
        return new LiveBroadcastResult(
                broadcast.getLiveBroadcastId(),
                broadcast.getBroadcastName(),
                broadcast.getStartTime(),
                broadcast.getEndTime(),
                broadcast.getBroadcastStatus(),
                broadcast.getHostId(),
                broadcast.getCompanyId(),
                broadcast.getTotalViewerCount()
        );
    }
}
