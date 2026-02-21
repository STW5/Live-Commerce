package com.live_commerce.coupon.domain.port.out;

import java.util.UUID;

/**
 * 주문 조회 포트 (Outbound) - 보상 트랜잭션용
 * - 구현체: adapter/out/client/OrderFeignAdapter
 */
public interface OrderQueryPort {
    OrderInfo getOrder(UUID orderId);

    record OrderInfo(UUID orderId, UUID userId, UUID couponId) {}
}
