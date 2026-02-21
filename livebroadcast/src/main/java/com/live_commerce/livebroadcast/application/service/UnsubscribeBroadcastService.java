package com.live_commerce.livebroadcast.application.service;

import com.live_commerce.livebroadcast.domain.exception.LiveBroadcastException;
import com.live_commerce.livebroadcast.domain.model.BroadcastSubscription;
import com.live_commerce.livebroadcast.domain.port.in.UnsubscribeBroadcastUseCase;
import com.live_commerce.livebroadcast.domain.port.out.BroadcastSubscriptionRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UnsubscribeBroadcastService implements UnsubscribeBroadcastUseCase {

    private final BroadcastSubscriptionRepositoryPort subscriptionRepository;

    @Override
    @Transactional
    public void unsubscribe(UUID userId, UUID broadcastId) {
        BroadcastSubscription sub = subscriptionRepository
                .findByUserIdAndBroadcastId(userId, broadcastId)
                .orElseThrow(LiveBroadcastException::forSubscriptionNotFound);

        subscriptionRepository.softDelete(sub.getSubscriptionId(), userId);
    }
}
