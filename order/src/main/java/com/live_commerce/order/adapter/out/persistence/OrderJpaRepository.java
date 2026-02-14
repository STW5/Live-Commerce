package com.live_commerce.order.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * OrderJpaEntity 전용 Spring Data JPA Repository
 *
 * [아키텍처 노트]
 * - 도메인 Order가 아닌 JPA 전용 OrderJpaEntity 기반
 * - adapter/out/persistence 레이어 전용 - domain 레이어에 노출되지 않음
 * - OrderPersistenceAdapter를 통해 OrderRepositoryPort를 구현
 */
public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID> {

    Page<OrderJpaEntity> findAllByUserId(UUID userId, Pageable pageable);

    Page<OrderJpaEntity> findAllByDeletedAtIsNull(Pageable pageable);
}
