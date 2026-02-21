package com.live_commerce.livebroadcast.application.service;

import com.live_commerce.livebroadcast.domain.exception.LiveBroadcastException;
import com.live_commerce.livebroadcast.domain.model.LiveBroadcast;
import com.live_commerce.livebroadcast.domain.port.in.DeleteBroadcastUseCase;
import com.live_commerce.livebroadcast.domain.port.out.BroadcastAlarmPort;
import com.live_commerce.livebroadcast.domain.port.out.LiveBroadcastRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeleteBroadcastService implements DeleteBroadcastUseCase {

    private final LiveBroadcastRepositoryPort broadcastRepository;
    private final BroadcastAlarmPort alarmPort;

    @Override
    @Transactional
    public void deleteBroadcast(UUID broadcastId, UUID userId, String role) {
        LiveBroadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(LiveBroadcastException::forLiveBroadcastNotFound);

        boolean isMaster = "ROLE_MASTER".equals(role);
        boolean isHost = broadcast.getHostId() != null && broadcast.getHostId().equals(userId);
        if (!isMaster && !isHost) {
            throw LiveBroadcastException.accessDenied();
        }

        broadcastRepository.softDelete(broadcastId, userId);
        alarmPort.deleteAlarm(broadcastId);
    }
}
