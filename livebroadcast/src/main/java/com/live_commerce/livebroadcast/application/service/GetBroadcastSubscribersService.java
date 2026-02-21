package com.live_commerce.livebroadcast.application.service;

import com.live_commerce.livebroadcast.domain.port.in.GetBroadcastSubscribersUseCase;
import com.live_commerce.livebroadcast.domain.port.out.BroadcastSubscriptionRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetBroadcastSubscribersService implements GetBroadcastSubscribersUseCase {

    private final BroadcastSubscriptionRepositoryPort subscriptionRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<UUID> getSubscribers(UUID broadcastId, Pageable pageable) {
        return subscriptionRepository.findSubscriberIdsByBroadcastId(broadcastId, pageable);
    }
}
