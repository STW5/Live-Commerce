package com.live_commerce.livebroadcast.application.service;

import com.live_commerce.livebroadcast.domain.exception.LiveBroadcastException;
import com.live_commerce.livebroadcast.domain.model.LiveBroadcast;
import com.live_commerce.livebroadcast.domain.port.in.RegisterBroadcastAlarmUseCase;
import com.live_commerce.livebroadcast.domain.port.out.BroadcastAlarmPort;
import com.live_commerce.livebroadcast.domain.port.out.LiveBroadcastRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegisterBroadcastAlarmService implements RegisterBroadcastAlarmUseCase {

    private final LiveBroadcastRepositoryPort broadcastRepository;
    private final BroadcastAlarmPort alarmPort;

    @Override
    @Transactional
    public void registerAlarm(UUID userId, UUID broadcastId) {
        LiveBroadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(LiveBroadcastException::forLiveBroadcastNotFound);

        if (broadcast.getStartTime() == null) {
            throw LiveBroadcastException.forInvalidAlarmRequest();
        }

        LocalDateTime notifyAt = broadcast.getStartTime().minusMinutes(10);
        alarmPort.registerAlarm(broadcastId, userId, notifyAt);
    }
}
