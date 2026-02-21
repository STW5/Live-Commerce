package com.live_commerce.coupon.adapter.out.persistence;

import com.live_commerce.coupon.domain.model.CouponPolicy;
import org.springframework.stereotype.Component;

@Component
public class CouponPolicyMapper {

    public CouponPolicyJpaEntity toJpaEntity(CouponPolicy domain) {
        return CouponPolicyJpaEntity.builder()
                .code(domain.getCode())
                .name(domain.getName())
                .discountType(domain.getDiscountType())
                .discountValue(domain.getDiscountValue())
                .minOrderAmt(domain.getMinOrderAmt())
                .maxOrderAmt(domain.getMaxOrderAmt())
                .startAt(domain.getStartAt())
                .endAt(domain.getEndAt())
                .isActive(domain.isActive())
                .build();
    }

    public CouponPolicy toDomain(CouponPolicyJpaEntity entity) {
        return CouponPolicy.reconstitute(
                entity.getCode(),
                entity.getName(),
                entity.getDiscountType(),
                entity.getDiscountValue(),
                entity.getMinOrderAmt(),
                entity.getMaxOrderAmt(),
                entity.getStartAt(),
                entity.getEndAt(),
                entity.isActive(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedBy(),
                entity.getUpdatedAt(),
                entity.getDeletedBy(),
                entity.getDeletedAt(),
                entity.getDeletedStatus()
        );
    }
}
