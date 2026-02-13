package com.live_commerce.events.inventory;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 재고 복구 이벤트 (Order → Product)
 * Producer: order-service
 * Consumer: product-service
 * Topic: inventory-rollback
 */
public record InventoryRollbackEvent(
    UUID orderId,
    UUID productId,
    int quantity,
    String reason,
    LocalDateTime rollbackAt
) {
    public static InventoryRollbackEvent of(UUID orderId, UUID productId, int quantity, String reason) {
        return new InventoryRollbackEvent(orderId, productId, quantity, reason, LocalDateTime.now());
    }
}
