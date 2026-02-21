package com.live_commerce.livebroadcast.domain.port.out;

import java.time.LocalDateTime;
import java.util.UUID;

public interface BroadcastAlarmPort {
    void registerAlarm(UUID broadcastId, UUID userId, LocalDateTime notifyAt);
    void deleteAlarm(UUID broadcastId);
}
