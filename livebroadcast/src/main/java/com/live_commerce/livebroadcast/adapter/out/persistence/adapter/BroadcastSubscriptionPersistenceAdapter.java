package com.live_commerce.livebroadcast.adapter.out.persistence.adapter;

import com.live_commerce.livebroadcast.adapter.out.persistence.entity.BroadcastSubscriptionJpaEntity;
import com.live_commerce.livebroadcast.adapter.out.persistence.repository.BroadcastSubscriptionJpaRepository;
import com.live_commerce.livebroadcast.domain.exception.LiveBroadcastException;
import com.live_commerce.livebroadcast.domain.model.BroadcastSubscription;
import com.live_commerce.livebroadcast.domain.port.out.BroadcastSubscriptionRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BroadcastSubscriptionPersistenceAdapter implements BroadcastSubscriptionRepositoryPort {

    private final BroadcastSubscriptionJpaRepository jpaRepository;

    @Override
    public BroadcastSubscription save(BroadcastSubscription subscription) {
        BroadcastSubscriptionJpaEntity entity = BroadcastSubscriptionJpaEntity.fromDomain(subscription);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<BroadcastSubscription> findByUserIdAndBroadcastId(UUID userId, UUID broadcastId) {
        return jpaRepository.findByUserIdAndBroadcastIdAndDeletedStatusFalse(userId, broadcastId)
                .map(BroadcastSubscriptionJpaEntity::toDomain);
    }

    @Override
    public boolean existsByUserIdAndBroadcastId(UUID userId, UUID broadcastId) {
        return jpaRepository.existsByUserIdAndBroadcastIdAndDeletedStatusFalse(userId, broadcastId);
    }

    @Override
    public List<BroadcastSubscription> findAllByUserId(UUID userId) {
        return jpaRepository.findAllByUserIdAndDeletedStatusFalse(userId)
                .stream().map(BroadcastSubscriptionJpaEntity::toDomain).toList();
    }

    @Override
    public Page<UUID> findSubscriberIdsByBroadcastId(UUID broadcastId, Pageable pageable) {
        return jpaRepository.findSubscriberIdsByBroadcastId(broadcastId, pageable);
    }

    @Override
    public void softDelete(UUID subscriptionId, UUID deletedById) {
        BroadcastSubscriptionJpaEntity entity = jpaRepository.findById(subscriptionId)
                .orElseThrow(LiveBroadcastException::forSubscriptionNotFound);
        entity.delete(deletedById);
        jpaRepository.save(entity);
    }
}
