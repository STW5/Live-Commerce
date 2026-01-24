package com.live_commerce.payment.domain.model.vo;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 결제 금액 Value Object
 * - 불변성 보장
 * - 비즈니스 규칙 캡슐화 (0보다 커야 함)
 */
public class PaymentAmount {

	private static final BigDecimal MIN_AMOUNT = BigDecimal.ZERO;
	private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000"); // 천만원 한도

	private final BigDecimal value;

	private PaymentAmount(BigDecimal value) {
		this.value = value;
	}

	public static PaymentAmount of(BigDecimal value) {
		validateAmount(value);
		return new PaymentAmount(value);
	}

	private static void validateAmount(BigDecimal value) {
		if (value == null) {
			throw new IllegalArgumentException("결제 금액은 필수입니다");
		}
		if (value.compareTo(MIN_AMOUNT) <= 0) {
			throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다");
		}
		if (value.compareTo(MAX_AMOUNT) > 0) {
			throw new IllegalArgumentException("결제 금액은 천만원을 초과할 수 없습니다");
		}
	}

	public BigDecimal getValue() {
		return value;
	}

	public boolean isGreaterThan(PaymentAmount other) {
		return this.value.compareTo(other.value) > 0;
	}

	public boolean isLessThan(PaymentAmount other) {
		return this.value.compareTo(other.value) < 0;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		PaymentAmount that = (PaymentAmount)o;
		return Objects.equals(value, that.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(value);
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
