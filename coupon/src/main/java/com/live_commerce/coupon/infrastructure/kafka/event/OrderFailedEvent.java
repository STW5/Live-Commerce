package com.live_commerce.coupon.infrastructure.kafka.event;

import java.util.UUID;

/**
 * 주문 실패 이벤트
 * Order 서비스에서 발행되며, 쿠폰 복구를 위해 사용됩니다.
 */
public record OrderFailedEvent(
        UUID orderId,
        String message
) {
}
