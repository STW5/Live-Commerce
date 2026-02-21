package com.live_commerce.coupon.application.dto.command;

import com.live_commerce.coupon.domain.model.DISCOUNT_TYPE;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateCouponPolicyCommand(
        String code,
        String name,
        DISCOUNT_TYPE discountType,
        BigDecimal discountValue,
        BigDecimal minOrderAmt,
        BigDecimal maxOrderAmt,
        LocalDateTime startAt,
        LocalDateTime endAt,
        boolean isActive
) {}
