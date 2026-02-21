package com.live_commerce.livebroadcast.application.service;

import com.live_commerce.livebroadcast.application.dto.command.CreateBroadcastCommand;
import com.live_commerce.livebroadcast.application.dto.result.LiveBroadcastResult;
import com.live_commerce.livebroadcast.domain.exception.LiveBroadcastException;
import com.live_commerce.livebroadcast.domain.model.LiveBroadcast;
import com.live_commerce.livebroadcast.domain.port.in.CreateBroadcastUseCase;
import com.live_commerce.livebroadcast.domain.port.out.BroadcastAlarmPort;
import com.live_commerce.livebroadcast.domain.port.out.ExternalCompanyPort;
import com.live_commerce.livebroadcast.domain.port.out.LiveBroadcastRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateBroadcastService implements CreateBroadcastUseCase {

    private final LiveBroadcastRepositoryPort broadcastRepository;
    private final ExternalCompanyPort companyPort;
    private final BroadcastAlarmPort alarmPort;

    @Override
    @Transactional
    public LiveBroadcastResult createBroadcast(CreateBroadcastCommand command, UUID hostId, UUID companyId) {
        if (!companyPort.existsCompany(companyId)) {
            throw LiveBroadcastException.forExternalCompanyNotFound();
        }

        if (command.startTime() != null && command.endTime() != null
                && command.endTime().isBefore(command.startTime())) {
            throw LiveBroadcastException.forInvalidTimeRange();
        }

        LiveBroadcast broadcast = LiveBroadcast.create(
                command.broadcastName(), command.startTime(), command.endTime(), hostId, companyId);
        LiveBroadcast saved = broadcastRepository.save(broadcast);

        if (saved.getStartTime() != null) {
            LocalDateTime notifyAt = saved.getStartTime().minusMinutes(20);
            alarmPort.registerAlarm(saved.getLiveBroadcastId(), hostId, notifyAt);
        }

        return LiveBroadcastResult.from(saved);
    }
}
