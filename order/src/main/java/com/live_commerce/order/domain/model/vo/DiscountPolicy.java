package com.live_commerce.order.domain.model.vo;

import com.live_commerce.order.domain.exception.OrderDomainException;

import java.math.BigDecimal;

/**
 * 할인 정책을 표현하는 Value Object
 * - 기존 DISCOUNT_TYPE enum + 할인 계산 로직 통합
 * - OrderCreateService / OrderModificationService의 인라인 할인 계산 코드를 대체
 */
public record DiscountPolicy(Type type, BigDecimal value) {

    public enum Type {
        FIXED,  // 고정 금액 할인
        RATE    // 비율(%) 할인
    }

    public DiscountPolicy {
        if (type == null) {
            throw new OrderDomainException("할인 타입은 null일 수 없습니다.");
        }
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new OrderDomainException("할인 값은 0 이상이어야 합니다.");
        }
        if (type == Type.RATE && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new OrderDomainException("할인율은 100%를 초과할 수 없습니다.");
        }
    }

    /**
     * 기존 OrderCreateService 인라인 할인 계산 로직을 VO로 이전
     * totalPrice에 할인을 적용하여 최종 결제 금액 반환
     */
    public Money apply(Money totalPrice) {
        return switch (type) {
            case FIXED -> {
                Money discounted = totalPrice.subtract(new Money(value));
                yield discounted.isGreaterThan(Money.ZERO) ? discounted : Money.ZERO;
            }
            case RATE -> {
                BigDecimal discountAmount = totalPrice.amount()
                        .multiply(value)
                        .divide(BigDecimal.valueOf(100));
                yield totalPrice.subtract(new Money(discountAmount));
            }
        };
    }
}
