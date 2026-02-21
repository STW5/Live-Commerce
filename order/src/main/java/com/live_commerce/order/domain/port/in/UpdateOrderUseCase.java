package com.live_commerce.order.domain.port.in;

import com.live_commerce.order.application.dto.command.UpdateOrderCommand;
import com.live_commerce.order.application.dto.result.OrderUpdateResult;

import java.util.UUID;

/**
 * 주문 수정 유즈케이스 (Inbound Port)
 * - PENDING 상태의 주문만 수정 가능
 * - 구현체: application/service/UpdateOrderService
 */
public interface UpdateOrderUseCase {
    OrderUpdateResult updateOrder(UUID orderId, UpdateOrderCommand command, UUID userId, String role);
}
