package com.live_commerce.order.application.service;

import com.live_commerce.order.application.dto.result.OrderDeleteResult;
import com.live_commerce.order.application.exception.OrderException;
import com.live_commerce.order.application.exception.OrderExceptionCode;
import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.port.in.DeleteOrderUseCase;
import com.live_commerce.order.domain.port.out.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 주문 소프트 삭제 서비스 (Hexagonal - DeleteOrderUseCase 구현체)
 * - ROLE_CUSTOMER: 본인 주문만 삭제 가능
 * - 소프트 삭제: deletedAt/deletedBy 설정 (OrderJpaEntity 담당)
 */
@Service
@RequiredArgsConstructor
public class DeleteOrderService implements DeleteOrderUseCase {

    private final OrderRepositoryPort orderRepositoryPort;

    @Override
    @Transactional
    public OrderDeleteResult deleteOrder(UUID orderId, UUID userId, String role) {
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderExceptionCode.NOT_FOUND));

        if ("ROLE_CUSTOMER".equals(role) && !order.getUserId().equals(userId)) {
            throw new OrderException("고객은 자신의 주문만 삭제할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        orderRepositoryPort.softDelete(orderId, userId.toString());
        return OrderDeleteResult.of(orderId);
    }
}
