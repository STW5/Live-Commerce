package com.live_commerce.coupon.domain.model.vo;

import com.live_commerce.coupon.domain.exception.CouponDomainException;
import java.time.LocalDateTime;

/**
 * 쿠폰 유효 기간 Value Object
 */
public record CouponPeriod(LocalDateTime startAt, LocalDateTime endAt) {

    public CouponPeriod {
        if (startAt == null || endAt == null) {
            throw new CouponDomainException("쿠폰 유효 기간은 필수입니다.");
        }
        if (endAt.isBefore(startAt)) {
            throw new CouponDomainException("쿠폰 종료일은 시작일 이후여야 합니다.");
        }
    }

    public boolean isValid() {
        return LocalDateTime.now().isBefore(endAt) && !LocalDateTime.now().isBefore(startAt);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(endAt);
    }
}
