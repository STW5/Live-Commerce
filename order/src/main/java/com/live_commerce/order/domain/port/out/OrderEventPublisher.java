package com.live_commerce.order.domain.port.out;

import java.util.UUID;

/**
 * 주문 이벤트 발행 포트 (Outbound)
 * - 구현체: adapter/out/messaging/KafkaOrderEventPublisher
 */
public interface OrderEventPublisher {
    /**
     * 재고 감소 이벤트 발행 (결제 완료 후)
     */
    void publishInventoryDecrease(UUID orderId, UUID productId, int quantity);

    /**
     * 쿠폰 사용 이벤트 발행
     */
    void publishCouponUsed(UUID couponId, UUID userId);

    /**
     * 재고 롤백 이벤트 발행 (결제 실패 시 보상 트랜잭션)
     */
    void publishInventoryRollback(UUID orderId, UUID productId, int quantity, String reason);

    /**
     * 주문 실패 이벤트 발행 (환불 + 쿠폰 복구 트리거)
     */
    void publishOrderFailed(UUID orderId, String reason);
}
