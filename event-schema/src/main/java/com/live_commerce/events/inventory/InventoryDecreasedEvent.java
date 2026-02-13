package com.live_commerce.events.inventory;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 재고 차감 완료 이벤트 (Product → Order)
 * Producer: product-service
 * Consumer: order-service
 * Topic: inventory-decreased
 */
public record InventoryDecreasedEvent(
    UUID orderId,
    UUID productId,
    int quantity,
    LocalDateTime decreasedAt
) {
    public static InventoryDecreasedEvent of(UUID orderId, UUID productId, int quantity) {
        return new InventoryDecreasedEvent(orderId, productId, quantity, LocalDateTime.now());
    }
}
