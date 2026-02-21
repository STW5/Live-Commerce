package com.live_commerce.livebroadcast.application.service;

import com.live_commerce.livebroadcast.application.dto.result.BroadcastProductResult;
import com.live_commerce.livebroadcast.application.dto.result.ExternalProductInfo;
import com.live_commerce.livebroadcast.domain.exception.LiveBroadcastException;
import com.live_commerce.livebroadcast.domain.model.BroadcastProduct;
import com.live_commerce.livebroadcast.domain.model.LiveBroadcast;
import com.live_commerce.livebroadcast.domain.port.in.ConnectBroadcastProductUseCase;
import com.live_commerce.livebroadcast.domain.port.out.BroadcastProductRepositoryPort;
import com.live_commerce.livebroadcast.domain.port.out.ExternalProductPort;
import com.live_commerce.livebroadcast.domain.port.out.LiveBroadcastRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConnectBroadcastProductService implements ConnectBroadcastProductUseCase {

    private final LiveBroadcastRepositoryPort broadcastRepository;
    private final BroadcastProductRepositoryPort productRepository;
    private final ExternalProductPort productPort;

    @Override
    @Transactional
    public BroadcastProductResult connectProduct(UUID broadcastId, UUID productId, UUID userId, String role) {
        LiveBroadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(LiveBroadcastException::forLiveBroadcastNotFound);

        boolean isMaster = "ROLE_MASTER".equals(role);
        boolean isHost = broadcast.getHostId() != null && broadcast.getHostId().equals(userId);
        if (!isMaster && !isHost) {
            throw LiveBroadcastException.accessDenied();
        }

        ExternalProductInfo product = productPort.getProduct(productId);
        if (product == null) {
            throw LiveBroadcastException.forExternalProductNotFound();
        }
        if (product.companyId() != null && !product.companyId().equals(broadcast.getCompanyId())) {
            throw LiveBroadcastException.companyMismatch();
        }

        if (productRepository.existsByBroadcastIdAndProductId(broadcastId, productId)) {
            throw LiveBroadcastException.forProductAlreadyConnected();
        }

        BroadcastProduct bp = BroadcastProduct.create(broadcastId, productId);
        return BroadcastProductResult.from(productRepository.save(bp));
    }
}
