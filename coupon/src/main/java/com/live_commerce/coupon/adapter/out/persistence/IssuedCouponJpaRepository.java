package com.live_commerce.coupon.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCouponJpaEntity, UUID> {

    Optional<IssuedCouponJpaEntity> findByIdAndUserIdAndIsUsedFalse(UUID id, UUID userId);

    Optional<IssuedCouponJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    List<IssuedCouponJpaEntity> findByUserId(UUID userId);
}
