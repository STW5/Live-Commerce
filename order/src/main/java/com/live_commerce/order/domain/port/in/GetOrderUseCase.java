package com.live_commerce.order.domain.port.in;

import com.live_commerce.order.application.dto.result.OrderGetResult;

import java.util.UUID;

/**
 * 주문 단건 조회 유즈케이스 (Inbound Port)
 * - 구현체: application/service/GetOrderService
 */
public interface GetOrderUseCase {
    OrderGetResult getOrder(UUID orderId, UUID userId, String role);
}
