package com.live_commerce.livebroadcast.domain.port.in;

import com.live_commerce.livebroadcast.application.dto.result.BroadcastProductResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface GetBroadcastProductsUseCase {
    Page<BroadcastProductResult> getProducts(UUID broadcastId, Pageable pageable);
}
