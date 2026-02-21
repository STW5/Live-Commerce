package com.live_commerce.livebroadcast.application.service;

import com.live_commerce.livebroadcast.application.dto.result.BroadcastProductResult;
import com.live_commerce.livebroadcast.domain.port.in.GetBroadcastProductsUseCase;
import com.live_commerce.livebroadcast.domain.port.out.BroadcastQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetBroadcastProductsService implements GetBroadcastProductsUseCase {

    private final BroadcastQueryPort queryPort;

    @Override
    @Transactional(readOnly = true)
    public Page<BroadcastProductResult> getProducts(UUID broadcastId, Pageable pageable) {
        Page<UUID> productIds = queryPort.findProductIdsByBroadcastId(broadcastId, pageable);
        List<BroadcastProductResult> content = productIds.getContent().stream()
                .map(productId -> new BroadcastProductResult(null, broadcastId, productId))
                .toList();
        return new PageImpl<>(content, pageable, productIds.getTotalElements());
    }
}
