package com.live_commerce.coupon.domain.repository;

import com.live_commerce.coupon.domain.model.IssuedCoupon;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IssuedCouponRepository extends JpaRepository<IssuedCoupon, UUID> {

  Optional<IssuedCoupon> findByIdAndUserIdAndIsUsedFalse(UUID couponId, UUID userId);

  Optional<IssuedCoupon> findByIdAndUserId(UUID couponId, UUID userId);

  List<IssuedCoupon> findByUserId(UUID userId);
}
