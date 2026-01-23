package com.live_commerce.order.domain.model;

public enum OrderStatus {
    PENDING("주문 접수"),
    READY("결제 대기"),
    PAID("결제 완료"),
    PROCESSING("상품 준비 중 및 배송 완료"),
    CANCELLED("주문 취소"),
    REFUNDED("환불 완료"),
    FAILED("주문 실패");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
