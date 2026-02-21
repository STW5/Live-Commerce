package com.live_commerce.coupon.domain.model.vo;

import com.live_commerce.coupon.domain.exception.CouponDomainException;

/**
 * 쿠폰 코드 Value Object
 */
public record CouponCode(String value) {

    public CouponCode {
        if (value == null || value.isBlank()) {
            throw new CouponDomainException("쿠폰 코드는 필수입니다.");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
