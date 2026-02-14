package com.live_commerce.order.domain.repository;

import com.live_commerce.order.adapter.out.persistence.OrderJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * 레거시 JPA Repository (OrderJpaEntity 기반)
 *
 * [아키텍처 노트]
 * - Order가 @Entity에서 순수 도메인으로 변경됨에 따라 OrderJpaEntity 기반으로 변경
 * - 레거시 OrderService, OrderModificationService 등에서 직접 사용
 * - 신규 코드는 OrderRepositoryPort (hexagonal) 사용 권장
 *
 * @deprecated 신규 코드는 OrderRepositoryPort 사용 권장. OrderService 리팩토링 완료 후 제거 예정.
 */
@Deprecated(since = "hexagonal-ddd-pilot", forRemoval = true)
public interface OrderRepository extends JpaRepository<OrderJpaEntity, UUID> {
    Page<OrderJpaEntity> findAllByUserId(UUID userId, Pageable pageable);
}
