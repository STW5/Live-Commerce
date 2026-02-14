package com.live_commerce.order.application.dto.command;

import java.util.UUID;

/**
 * 주문 생성 Command
 * - Controller에서 Application Layer로 전달되는 입력 객체
 * - @Valid 검증은 Controller 레이어에서 수행
 */
public record CreateOrderCommand(
        UUID userId,
        UUID productId,
        int orderQuantity,
        String requirement,
        UUID broadcastId,
        UUID couponId    // nullable
) {}
