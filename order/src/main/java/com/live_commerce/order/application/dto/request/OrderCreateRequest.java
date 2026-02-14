package com.live_commerce.order.application.dto.request;

import jakarta.validation.constraints.Min;

import java.util.UUID;

/**
 * 주문 요청 DTO
 *
 * [아키텍처 노트]
 * - Order.builder() 제거에 따라 toOrder() 메서드 삭제
 * - 주문 생성 로직은 OrderCreateService(레거시) 또는 CreateOrderService(헥사고날)가 담당
 */
public record OrderCreateRequest(
        UUID productId,
        @Min(1) int orderQuantity,
        String requirement,
        UUID broadcastId,
        UUID couponId
) {
}
