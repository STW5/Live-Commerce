package com.live_commerce.livebroadcast.application.service;

import com.live_commerce.livebroadcast.application.dto.result.BroadcastSubscriptionResult;
import com.live_commerce.livebroadcast.domain.exception.LiveBroadcastException;
import com.live_commerce.livebroadcast.domain.model.BroadcastSubscription;
import com.live_commerce.livebroadcast.domain.port.in.SubscribeBroadcastUseCase;
import com.live_commerce.livebroadcast.domain.port.out.BroadcastSubscriptionRepositoryPort;
import com.live_commerce.livebroadcast.domain.port.out.LiveBroadcastRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscribeBroadcastService implements SubscribeBroadcastUseCase {

    private final BroadcastSubscriptionRepositoryPort subscriptionRepository;
    private final LiveBroadcastRepositoryPort broadcastRepository;

    @Override
    @Transactional
    public BroadcastSubscriptionResult subscribe(UUID userId, UUID broadcastId) {
        broadcastRepository.findById(broadcastId)
                .orElseThrow(LiveBroadcastException::forLiveBroadcastNotFound);

        if (subscriptionRepository.existsByUserIdAndBroadcastId(userId, broadcastId)) {
            throw LiveBroadcastException.alreadySubscribed();
        }

        BroadcastSubscription sub = BroadcastSubscription.create(userId, broadcastId);
        return BroadcastSubscriptionResult.from(subscriptionRepository.save(sub));
    }
}
