package com.live_commerce.coupon.adapter.out.persistence;

import com.live_commerce.coupon.domain.model.IssuedCoupon;
import com.live_commerce.coupon.domain.port.out.IssuedCouponRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 발급 쿠폰 영속성 어댑터 (IssuedCouponRepositoryPort 구현체)
 */
@Component
@RequiredArgsConstructor
public class IssuedCouponPersistenceAdapter implements IssuedCouponRepositoryPort {

    private final IssuedCouponJpaRepository jpaRepository;
    private final IssuedCouponMapper mapper;

    @Override
    public IssuedCoupon save(IssuedCoupon issuedCoupon) {
        IssuedCouponJpaEntity entity = mapper.toJpaEntity(issuedCoupon);
        IssuedCouponJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<IssuedCoupon> findByIdAndUserIdAndNotUsed(UUID couponId, UUID userId) {
        return jpaRepository.findByIdAndUserIdAndIsUsedFalse(couponId, userId)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<IssuedCoupon> findByIdAndUserId(UUID couponId, UUID userId) {
        return jpaRepository.findByIdAndUserId(couponId, userId).map(mapper::toDomain);
    }

    @Override
    public List<IssuedCoupon> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}
