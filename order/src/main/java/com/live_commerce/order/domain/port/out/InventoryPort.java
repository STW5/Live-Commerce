package com.live_commerce.order.domain.port.out;

import java.util.UUID;

/**
 * 재고 쓰기 작업 Port (Outbound)
 * - Kafka 이벤트 발행을 통한 재고 감소/복구
 * - 읽기 작업(isOrderable, findById)은 ProductQueryPort 담당
 * - 구현체: adapter/out/messaging/KafkaInventoryEventPublisher
 */
public interface InventoryPort {
    /**
     * 재고 감소 이벤트 발행 (Kafka: inventory-decrease)
     */
    void decreaseInventory(UUID productId, int quantity, UUID orderId);

    /**
     * 재고 복구 이벤트 발행 (Kafka: inventory-rollback) — 보상 트랜잭션용
     */
    void rollbackInventory(UUID productId, int quantity, UUID orderId);
}
