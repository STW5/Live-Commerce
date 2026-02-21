package com.live_commerce.order.domain.port.in;

import com.live_commerce.order.application.dto.result.OrderDeleteResult;

import java.util.UUID;

/**
 * 주문 소프트 삭제 유즈케이스 (Inbound Port)
 * - 구현체: application/service/DeleteOrderService
 */
public interface DeleteOrderUseCase {
    OrderDeleteResult deleteOrder(UUID orderId, UUID userId, String role);
}
