package com.live_commerce.coupon.adapter.out.persistence;

import com.live_commerce.coupon.domain.model.IssuedCoupon;
import org.springframework.stereotype.Component;

@Component
public class IssuedCouponMapper {

    public IssuedCouponJpaEntity toJpaEntity(IssuedCoupon domain) {
        return IssuedCouponJpaEntity.builder()
                .id(domain.getId())
                .userId(domain.getUserId())
                .couponCode(domain.getCouponCode())
                .isUsed(domain.getIsUsed())
                .usedAt(domain.getUsedAt())
                .expiresAt(domain.getExpiresAt())
                .build();
    }

    public IssuedCoupon toDomain(IssuedCouponJpaEntity entity) {
        return IssuedCoupon.reconstitute(
                entity.getId(),
                entity.getUserId(),
                entity.getCouponCode(),
                entity.getIsUsed(),
                entity.getUsedAt(),
                entity.getExpiresAt()
        );
    }
}
