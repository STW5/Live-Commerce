package com.live_commerce.livebroadcast.adapter.out.client;

import com.live_commerce.livebroadcast.domain.port.out.BroadcastAlarmPort;
import com.live_commerce.livebroadcast.infrastructure.client.notification.BroadcastAlarmRegisterRequest;
import com.live_commerce.livebroadcast.infrastructure.client.notification.NotificationClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NotificationFeignAdapter implements BroadcastAlarmPort {

    private final NotificationClient notificationClient;

    @Override
    public void registerAlarm(UUID broadcastId, UUID userId, LocalDateTime notifyAt) {
        notificationClient.registerBroadcastAlarm(
                new BroadcastAlarmRegisterRequest("LIVE_BROADCAST", broadcastId, notifyAt));
    }

    @Override
    public void deleteAlarm(UUID broadcastId) {
        notificationClient.unregisterBroadcastAlarm(broadcastId);
    }
}
