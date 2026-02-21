package com.live_commerce.coupon.application.dto.result;

import com.live_commerce.coupon.domain.model.IssuedCoupon;
import java.time.LocalDateTime;
import java.util.UUID;

public record IssuedCouponResult(
        UUID id,
        UUID userId,
        String couponCode,
        Boolean isUsed,
        LocalDateTime usedAt,
        LocalDateTime expiresAt
) {
    public static IssuedCouponResult from(IssuedCoupon coupon) {
        return new IssuedCouponResult(
                coupon.getId(),
                coupon.getUserId(),
                coupon.getCouponCode(),
                coupon.getIsUsed(),
                coupon.getUsedAt(),
                coupon.getExpiresAt()
        );
    }
}
