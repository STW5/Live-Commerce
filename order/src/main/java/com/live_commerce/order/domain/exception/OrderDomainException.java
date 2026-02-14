package com.live_commerce.order.domain.exception;

/**
 * Domain Layer 전용 예외 - 순수 Java, Spring/HTTP 의존 없음
 * 비즈니스 규칙 위반 시 사용
 */
public class OrderDomainException extends RuntimeException {

    public OrderDomainException(String message) {
        super(message);
    }

    public OrderDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
