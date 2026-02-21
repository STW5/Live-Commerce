package com.live_commerce.order.application.dto.result;

import com.live_commerce.order.domain.model.Order;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 주문 목록 조회 결과 (페이징)
 */
public record OrderListResult(
        List<OrderGetResult> orders,
        int totalPages,
        long totalElements
) {
    public static OrderListResult from(Page<Order> page) {
        return new OrderListResult(
                page.map(OrderGetResult::from).toList(),
                page.getTotalPages(),
                page.getTotalElements()
        );
    }
}
