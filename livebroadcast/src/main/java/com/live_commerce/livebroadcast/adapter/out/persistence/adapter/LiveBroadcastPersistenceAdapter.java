package com.live_commerce.livebroadcast.adapter.out.persistence.adapter;

import com.live_commerce.livebroadcast.adapter.out.persistence.entity.LiveBroadcastJpaEntity;
import com.live_commerce.livebroadcast.adapter.out.persistence.repository.LiveBroadcastJpaRepository;
import com.live_commerce.livebroadcast.domain.exception.LiveBroadcastException;
import com.live_commerce.livebroadcast.domain.model.BroadcastStatus;
import com.live_commerce.livebroadcast.domain.model.LiveBroadcast;
import com.live_commerce.livebroadcast.domain.port.out.LiveBroadcastRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LiveBroadcastPersistenceAdapter implements LiveBroadcastRepositoryPort {

    private final LiveBroadcastJpaRepository jpaRepository;

    @Override
    public LiveBroadcast save(LiveBroadcast broadcast) {
        LiveBroadcastJpaEntity entity = LiveBroadcastJpaEntity.fromDomain(broadcast);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<LiveBroadcast> findById(UUID broadcastId) {
        return jpaRepository.findByLiveBroadcastIdAndDeletedStatusFalse(broadcastId)
                .map(LiveBroadcastJpaEntity::toDomain);
    }

    @Override
    public boolean existsById(UUID broadcastId) {
        return jpaRepository.existsByLiveBroadcastIdAndDeletedStatusFalse(broadcastId);
    }

    @Override
    public List<LiveBroadcast> findAllByStatusIn(List<BroadcastStatus> statuses) {
        return jpaRepository.findAllByDeletedStatusFalseAndBroadcastStatusIn(statuses)
                .stream().map(LiveBroadcastJpaEntity::toDomain).toList();
    }

    @Override
    public UUID findHostIdByBroadcastId(UUID broadcastId) {
        return jpaRepository.findHostIdByBroadcastId(broadcastId);
    }

    @Override
    public void softDelete(UUID broadcastId, UUID deletedById) {
        LiveBroadcastJpaEntity entity = jpaRepository
                .findByLiveBroadcastIdAndDeletedStatusFalse(broadcastId)
                .orElseThrow(LiveBroadcastException::forLiveBroadcastNotFound);
        entity.delete(deletedById);
        jpaRepository.save(entity);
    }
}
