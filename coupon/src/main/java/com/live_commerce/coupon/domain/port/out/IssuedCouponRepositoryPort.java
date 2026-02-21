package com.live_commerce.coupon.domain.port.out;

import com.live_commerce.coupon.domain.model.IssuedCoupon;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 발급 쿠폰 영속성 포트 (Outbound)
 * - Domain Layer에 위치: 순수 인터페이스
 * - 구현체: adapter/out/persistence/IssuedCouponPersistenceAdapter
 */
public interface IssuedCouponRepositoryPort {
    IssuedCoupon save(IssuedCoupon issuedCoupon);
    Optional<IssuedCoupon> findByIdAndUserIdAndNotUsed(UUID couponId, UUID userId);
    Optional<IssuedCoupon> findByIdAndUserId(UUID couponId, UUID userId);
    List<IssuedCoupon> findByUserId(UUID userId);
}
