package com.live_commerce.order.application.dto.result;

import java.util.UUID;

/**
 * 주문 삭제 결과
 */
public record OrderDeleteResult(
        UUID orderId,
        String message
) {
    public static OrderDeleteResult of(UUID orderId) {
        return new OrderDeleteResult(orderId, "주문 삭제가 완료되었습니다.");
    }
}
