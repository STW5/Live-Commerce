package com.live_commerce.coupon.application.dto.result;

import com.live_commerce.coupon.domain.model.CouponPolicy;
import com.live_commerce.coupon.domain.model.DISCOUNT_TYPE;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponPolicyResult(
        String code,
        String name,
        DISCOUNT_TYPE discountType,
        BigDecimal discountValue,
        BigDecimal minOrderAmt,
        BigDecimal maxOrderAmt,
        LocalDateTime startAt,
        LocalDateTime endAt,
        boolean isActive
) {
    public static CouponPolicyResult from(CouponPolicy policy) {
        return new CouponPolicyResult(
                policy.getCode(),
                policy.getName(),
                policy.getDiscountType(),
                policy.getDiscountValue(),
                policy.getMinOrderAmt(),
                policy.getMaxOrderAmt(),
                policy.getStartAt(),
                policy.getEndAt(),
                policy.isActive()
        );
    }
}
