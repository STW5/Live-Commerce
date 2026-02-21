package com.live_commerce.order.application.service;

import com.live_commerce.order.application.dto.result.OrderListResult;
import com.live_commerce.order.domain.port.in.GetOrderListUseCase;
import com.live_commerce.order.domain.port.out.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 주문 목록 조회 서비스 (Hexagonal - GetOrderListUseCase 구현체)
 * - ROLE_CUSTOMER: 본인 주문 목록만 조회
 * - 관리자 권한: 전체 주문 목록 조회
 */
@Service
@RequiredArgsConstructor
public class GetOrderListService implements GetOrderListUseCase {

    private final OrderRepositoryPort orderRepositoryPort;

    @Override
    @Transactional(readOnly = true)
    public OrderListResult getOrders(UUID userId, String role, Pageable pageable) {
        if ("ROLE_CUSTOMER".equals(role)) {
            return OrderListResult.from(orderRepositoryPort.findAllByUserId(userId, pageable));
        }
        return OrderListResult.from(orderRepositoryPort.findAll(pageable));
    }
}
