package com.live_commerce.order.adapter.out.messaging;

import com.live_commerce.events.inventory.InventoryDecreaseRequestEvent;
import com.live_commerce.events.inventory.InventoryRollbackEvent;
import com.live_commerce.events.order.OrderFailedEvent;
import com.live_commerce.order.domain.port.out.OrderEventPublisher;
import com.live_commerce.order.infrastructure.outbox.OutboxEventHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * OrderEventPublisher 구현체 (Kafka Messaging Adapter)
 * - Application Layer가 Kafka 구현체에 직접 의존하지 않도록 격리
 * - 모든 이벤트 발행을 Outbox 패턴으로 처리하여 원자성 보장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaOrderEventPublisher implements OrderEventPublisher {

    private final OutboxEventHelper outboxEventHelper;

    @Override
    public void publishInventoryDecrease(UUID orderId, UUID productId, int quantity) {
        InventoryDecreaseRequestEvent event = InventoryDecreaseRequestEvent.of(orderId, productId, quantity);
        outboxEventHelper.saveEvent("ORDER", orderId, "INVENTORY_DECREASE",
                "inventory-decrease", event);
        log.info("[KafkaOrderEventPublisher] 재고 감소 이벤트 Outbox 저장 - orderId: {}", orderId);
    }

    @Override
    public void publishCouponUsed(UUID couponId, UUID userId) {
        CouponUsedEvent event = new CouponUsedEvent(couponId, userId);
        outboxEventHelper.saveEvent("ORDER", couponId, "COUPON_USED", "coupon-used", event);
        log.info("[KafkaOrderEventPublisher] 쿠폰 사용 이벤트 Outbox 저장 - couponId: {}", couponId);
    }

    @Override
    public void publishInventoryRollback(UUID orderId, UUID productId, int quantity, String reason) {
        InventoryRollbackEvent rollbackEvent = InventoryRollbackEvent.of(orderId, productId, quantity, reason);
        outboxEventHelper.saveEvent("ORDER", orderId, "INVENTORY_ROLLBACK",
                "inventory-rollback", rollbackEvent);
        log.info("[KafkaOrderEventPublisher] 재고 롤백 이벤트 Outbox 저장 - orderId: {}", orderId);
    }

    @Override
    public void publishOrderFailed(UUID orderId, String reason) {
        OrderFailedEvent event = OrderFailedEvent.of(orderId, "주문 처리 실패: " + reason);
        outboxEventHelper.saveEvent("ORDER", orderId, "ORDER_FAILED",
                "order-failed", event);
        log.info("[KafkaOrderEventPublisher] 주문 실패 이벤트 Outbox 저장 - orderId: {}", orderId);
    }
}
