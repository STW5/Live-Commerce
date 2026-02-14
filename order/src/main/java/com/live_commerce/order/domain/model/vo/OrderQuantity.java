package com.live_commerce.order.domain.model.vo;

import com.live_commerce.order.domain.exception.OrderDomainException;

/**
 * 주문 수량을 표현하는 Value Object
 * - 최소 1 이상 보장
 */
public record OrderQuantity(int value) {

    public OrderQuantity {
        if (value < 1) {
            throw new OrderDomainException("주문 수량은 1 이상이어야 합니다. value=" + value);
        }
    }
}
