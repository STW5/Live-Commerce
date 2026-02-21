package com.live_commerce.livebroadcast.application.service;

import com.live_commerce.livebroadcast.application.dto.command.UpdateBroadcastCommand;
import com.live_commerce.livebroadcast.application.dto.result.LiveBroadcastResult;
import com.live_commerce.livebroadcast.domain.exception.LiveBroadcastException;
import com.live_commerce.livebroadcast.domain.model.LiveBroadcast;
import com.live_commerce.livebroadcast.domain.port.in.UpdateBroadcastUseCase;
import com.live_commerce.livebroadcast.domain.port.out.BroadcastAlarmPort;
import com.live_commerce.livebroadcast.domain.port.out.LiveBroadcastRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateBroadcastService implements UpdateBroadcastUseCase {

    private final LiveBroadcastRepositoryPort broadcastRepository;
    private final BroadcastAlarmPort alarmPort;

    @Override
    @Transactional
    public LiveBroadcastResult updateBroadcast(UUID broadcastId, UpdateBroadcastCommand command,
            UUID userId, String role) {
        LiveBroadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(LiveBroadcastException::forLiveBroadcastNotFound);

        validateOwnerOrMaster(broadcast, userId, role);

        broadcast.update(command.broadcastName(), command.startTime(),
                command.endTime(), command.broadcastStatus());
        LiveBroadcast saved = broadcastRepository.save(broadcast);

        if (command.startTime() != null) {
            alarmPort.deleteAlarm(broadcastId);
            LocalDateTime notifyAt = saved.getStartTime().minusMinutes(20);
            alarmPort.registerAlarm(broadcastId, userId, notifyAt);
        }

        return LiveBroadcastResult.from(saved);
    }

    private void validateOwnerOrMaster(LiveBroadcast broadcast, UUID userId, String role) {
        boolean isMaster = "ROLE_MASTER".equals(role);
        boolean isHost = broadcast.getHostId() != null && broadcast.getHostId().equals(userId);
        if (!isMaster && !isHost) {
            throw LiveBroadcastException.accessDenied();
        }
    }
}
