package com.live_commerce.order.domain.port.in;

import com.live_commerce.order.application.dto.result.OrderListResult;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * 주문 목록 조회 유즈케이스 (Inbound Port)
 * - 구현체: application/service/GetOrderListService
 */
public interface GetOrderListUseCase {
    OrderListResult getOrders(UUID userId, String role, Pageable pageable);
}
