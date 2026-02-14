package com.live_commerce.order.domain.port.out;

import com.live_commerce.order.domain.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

/**
 * 주문 영속성 포트 (Outbound)
 * - Domain Layer에 위치: 순수 인터페이스
 * - JPA, Spring Data 등 인프라 의존 없음
 * - 구현체: adapter/out/persistence/OrderPersistenceAdapter
 */
public interface OrderRepositoryPort {
    Order save(Order order);
    Optional<Order> findById(UUID orderId);
    Page<Order> findAllByUserId(UUID userId, Pageable pageable);
    Page<Order> findAll(Pageable pageable);

    /**
     * 소프트 삭제 (deletedAt, deletedBy 설정)
     * OrderJpaEntity(BaseJpaEntity)의 delete() 메서드 위임
     */
    void softDelete(UUID orderId, String deletedBy);
}
