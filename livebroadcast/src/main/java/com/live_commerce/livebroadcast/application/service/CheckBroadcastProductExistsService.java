package com.live_commerce.livebroadcast.application.service;

import com.live_commerce.livebroadcast.domain.port.in.CheckBroadcastProductExistsUseCase;
import com.live_commerce.livebroadcast.domain.port.out.BroadcastProductRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckBroadcastProductExistsService implements CheckBroadcastProductExistsUseCase {

    private final BroadcastProductRepositoryPort productRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID broadcastId, UUID productId) {
        return productRepository.existsByBroadcastIdAndProductId(broadcastId, productId);
    }
}
