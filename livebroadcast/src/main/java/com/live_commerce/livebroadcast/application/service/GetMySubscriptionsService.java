package com.live_commerce.livebroadcast.application.service;

import com.live_commerce.livebroadcast.application.dto.result.BroadcastSubscriptionResult;
import com.live_commerce.livebroadcast.domain.port.in.GetMySubscriptionsUseCase;
import com.live_commerce.livebroadcast.domain.port.out.BroadcastSubscriptionRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetMySubscriptionsService implements GetMySubscriptionsUseCase {

    private final BroadcastSubscriptionRepositoryPort subscriptionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<BroadcastSubscriptionResult> getMySubscriptions(UUID userId) {
        return subscriptionRepository.findAllByUserId(userId)
                .stream()
                .map(BroadcastSubscriptionResult::from)
                .toList();
    }
}
