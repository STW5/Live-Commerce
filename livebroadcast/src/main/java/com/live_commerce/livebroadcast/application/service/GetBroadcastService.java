package com.live_commerce.livebroadcast.application.service;

import com.live_commerce.livebroadcast.application.dto.result.LiveBroadcastResult;
import com.live_commerce.livebroadcast.domain.exception.LiveBroadcastException;
import com.live_commerce.livebroadcast.domain.port.in.GetBroadcastUseCase;
import com.live_commerce.livebroadcast.domain.port.out.LiveBroadcastRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetBroadcastService implements GetBroadcastUseCase {

    private final LiveBroadcastRepositoryPort broadcastRepository;

    @Override
    @Transactional(readOnly = true)
    public LiveBroadcastResult getBroadcast(UUID broadcastId) {
        return broadcastRepository.findById(broadcastId)
                .map(LiveBroadcastResult::from)
                .orElseThrow(LiveBroadcastException::forLiveBroadcastNotFound);
    }
}
