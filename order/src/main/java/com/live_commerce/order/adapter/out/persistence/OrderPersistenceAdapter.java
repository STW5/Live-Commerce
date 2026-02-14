package com.live_commerce.order.adapter.out.persistence;

import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.port.out.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * OrderRepositoryPort 구현체 (Persistence Adapter)
 *
 * [아키텍처 노트]
 * - OrderJpaRepository(JPA) + OrderMapper(변환) 사용
 * - Order 도메인 객체 ↔ OrderJpaEntity JPA 엔티티 변환은 OrderMapper 담당
 * - 도메인 레이어가 JPA를 모르도록 격리
 */
@Component
@RequiredArgsConstructor
public class OrderPersistenceAdapter implements OrderRepositoryPort {

    private final OrderJpaRepository jpaRepository;
    private final OrderMapper mapper;

    @Override
    public Order save(Order order) {
        OrderJpaEntity entity = mapper.toJpaEntity(order);
        OrderJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Order> findById(UUID orderId) {
        return jpaRepository.findById(orderId).map(mapper::toDomain);
    }

    @Override
    public Page<Order> findAllByUserId(UUID userId, Pageable pageable) {
        return jpaRepository.findAllByUserId(userId, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Order> findAll(Pageable pageable) {
        return jpaRepository.findAllByDeletedAtIsNull(pageable).map(mapper::toDomain);
    }

    @Override
    public void softDelete(UUID orderId, String deletedBy) {
        jpaRepository.findById(orderId).ifPresent(entity -> {
            entity.delete(deletedBy);
            jpaRepository.save(entity);
        });
    }
}
