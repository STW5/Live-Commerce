package com.live_commerce.events.inventory;

import java.util.UUID;

/**
 * 재고 감소 요청 이벤트 (Order → Product)
 * Producer: order-service
 * Consumer: product-service
 * Topic: inventory-decrease
 */
public record InventoryDecreaseRequestEvent(
    UUID orderId,
    UUID productId,
    int quantity
) {
    public static InventoryDecreaseRequestEvent of(UUID orderId, UUID productId, int quantity) {
        return new InventoryDecreaseRequestEvent(orderId, productId, quantity);
    }
}
