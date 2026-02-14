package com.live_commerce.order.application.dto.request;

import lombok.Builder;

import java.util.UUID;

@Builder
public record OrderUpdateRequest(
        Integer productQuantity,
        String requirement,
        UUID couponId
) {
    // Order.builder()가 제거됨에 따라 toOrder() 삭제.
    // OrderModificationService에서 order.applyUpdate() 직접 호출
}
