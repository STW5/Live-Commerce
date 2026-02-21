package com.live_commerce.coupon.domain.exception;

/**
 * 쿠폰 도메인 예외 (순수 Java - Spring/HTTP 의존 없음)
 */
public class CouponDomainException extends RuntimeException {

    public CouponDomainException(String message) {
        super(message);
    }

    public CouponDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
