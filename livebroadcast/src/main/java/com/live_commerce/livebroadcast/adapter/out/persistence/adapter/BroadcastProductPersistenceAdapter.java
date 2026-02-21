package com.live_commerce.livebroadcast.adapter.out.persistence.adapter;

import com.live_commerce.livebroadcast.adapter.out.persistence.entity.BroadcastProductJpaEntity;
import com.live_commerce.livebroadcast.adapter.out.persistence.repository.BroadcastProductJpaRepository;
import com.live_commerce.livebroadcast.domain.exception.LiveBroadcastException;
import com.live_commerce.livebroadcast.domain.model.BroadcastProduct;
import com.live_commerce.livebroadcast.domain.port.out.BroadcastProductRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BroadcastProductPersistenceAdapter implements BroadcastProductRepositoryPort {

    private final BroadcastProductJpaRepository jpaRepository;

    @Override
    public BroadcastProduct save(BroadcastProduct broadcastProduct) {
        BroadcastProductJpaEntity entity = BroadcastProductJpaEntity.fromDomain(broadcastProduct);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<BroadcastProduct> findByBroadcastIdAndProductId(UUID broadcastId, UUID productId) {
        return jpaRepository.findByLiveBroadcastIdAndProductIdAndDeletedStatusFalse(broadcastId, productId)
                .map(BroadcastProductJpaEntity::toDomain);
    }

    @Override
    public boolean existsByBroadcastIdAndProductId(UUID broadcastId, UUID productId) {
        return jpaRepository.existsByLiveBroadcastIdAndProductIdAndDeletedStatusFalse(broadcastId, productId);
    }

    @Override
    public void softDelete(UUID broadcastProductId, UUID deletedById) {
        BroadcastProductJpaEntity entity = jpaRepository.findById(broadcastProductId)
                .orElseThrow(LiveBroadcastException::forLiveBroadcastNotFound);
        entity.delete(deletedById);
        jpaRepository.save(entity);
    }
}
