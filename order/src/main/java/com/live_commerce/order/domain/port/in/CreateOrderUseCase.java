package com.live_commerce.order.domain.port.in;

import com.live_commerce.order.application.dto.command.CreateOrderCommand;
import com.live_commerce.order.application.dto.result.CreateOrderResult;

/**
 * 주문 생성 유즈케이스 (Inbound Port)
 * - 구현체: application/service/CreateOrderService
 */
public interface CreateOrderUseCase {
    CreateOrderResult createOrder(CreateOrderCommand command);
}
