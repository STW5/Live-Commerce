package com.live_commerce.livebroadcast.domain.port.in;

import com.live_commerce.livebroadcast.application.dto.result.BroadcastSubscriptionResult;

import java.util.List;
import java.util.UUID;

public interface GetMySubscriptionsUseCase {
    List<BroadcastSubscriptionResult> getMySubscriptions(UUID userId);
}
