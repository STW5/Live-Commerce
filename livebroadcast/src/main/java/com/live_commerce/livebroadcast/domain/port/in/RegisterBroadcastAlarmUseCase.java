package com.live_commerce.livebroadcast.domain.port.in;

import java.util.UUID;

public interface RegisterBroadcastAlarmUseCase {
    void registerAlarm(UUID userId, UUID broadcastId);
}
