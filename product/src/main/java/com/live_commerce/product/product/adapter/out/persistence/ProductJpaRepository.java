package com.live_commerce.product.product.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, UUID> {

    Optional<ProductJpaEntity> findByProductIdAndDeletedStatusFalse(UUID productId);

    boolean existsByProductIdAndDeletedStatusFalse(UUID productId);

    List<ProductJpaEntity> findAllByProductIdInAndDeletedStatusFalse(List<UUID> productIds);
}
