package com.live_commerce.order.domain.model.vo;

import com.live_commerce.order.domain.exception.OrderDomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 금액을 표현하는 Value Object
 * - 불변 (record)
 * - 음수 불가
 * - 산술 연산 메서드 포함
 */
public record Money(BigDecimal amount) {

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new OrderDomainException("금액은 0 이상이어야 합니다. amount=" + amount);
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(double value) {
        return new Money(BigDecimal.valueOf(value));
    }

    public static Money of(int value) {
        return new Money(BigDecimal.valueOf(value));
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money subtract(Money other) {
        return new Money(this.amount.subtract(other.amount));
    }

    public Money multiply(int multiplier) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(multiplier)));
    }

    public boolean isGreaterThan(Money other) {
        return this.amount.compareTo(other.amount) > 0;
    }

    public double toDouble() {
        return amount.doubleValue();
    }
}
