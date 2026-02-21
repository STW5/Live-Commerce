package com.live_commerce.livebroadcast.application.service;

import com.live_commerce.livebroadcast.domain.exception.LiveBroadcastException;
import com.live_commerce.livebroadcast.domain.model.BroadcastProduct;
import com.live_commerce.livebroadcast.domain.model.LiveBroadcast;
import com.live_commerce.livebroadcast.domain.port.in.DisconnectBroadcastProductUseCase;
import com.live_commerce.livebroadcast.domain.port.out.BroadcastProductRepositoryPort;
import com.live_commerce.livebroadcast.domain.port.out.LiveBroadcastRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DisconnectBroadcastProductService implements DisconnectBroadcastProductUseCase {

    private final LiveBroadcastRepositoryPort broadcastRepository;
    private final BroadcastProductRepositoryPort productRepository;

    @Override
    @Transactional
    public void disconnectProduct(UUID broadcastId, UUID productId, UUID userId, String role) {
        LiveBroadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(LiveBroadcastException::forLiveBroadcastNotFound);

        boolean isMaster = "ROLE_MASTER".equals(role);
        boolean isHost = broadcast.getHostId() != null && broadcast.getHostId().equals(userId);
        if (!isMaster && !isHost) {
            throw LiveBroadcastException.accessDenied();
        }

        BroadcastProduct bp = productRepository.findByBroadcastIdAndProductId(broadcastId, productId)
                .orElseThrow(LiveBroadcastException::forConnectedProductNotFound);

        productRepository.softDelete(bp.getBroadcastProductId(), userId);
    }
}
