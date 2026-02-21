package com.live_commerce.order.application.service;

import com.live_commerce.order.application.dto.result.OrderGetResult;
import com.live_commerce.order.application.exception.OrderException;
import com.live_commerce.order.application.exception.OrderExceptionCode;
import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.port.in.GetOrderUseCase;
import com.live_commerce.order.domain.port.out.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 주문 단건 조회 서비스 (Hexagonal - GetOrderUseCase 구현체)
 * - ROLE_CUSTOMER: 본인 주문만 조회 가능
 * - 관리자 권한: 모든 주문 조회 가능
 */
@Service
@RequiredArgsConstructor
public class GetOrderService implements GetOrderUseCase {

    private final OrderRepositoryPort orderRepositoryPort;

    @Override
    @Transactional(readOnly = true)
    public OrderGetResult getOrder(UUID orderId, UUID userId, String role) {
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderExceptionCode.NOT_FOUND));

        if ("ROLE_CUSTOMER".equals(role) && !order.getUserId().equals(userId)) {
            throw new OrderException("고객은 자신의 주문만 조회할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        return OrderGetResult.from(order);
    }
}
