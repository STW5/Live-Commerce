package com.live_commerce.order.application.dto.command;

import java.util.UUID;

/**
 * 주문 수정 커맨드 (Application Layer Input)
 */
public record UpdateOrderCommand(
        Integer productQuantity,
        String requirement,
        UUID couponId
) {}
