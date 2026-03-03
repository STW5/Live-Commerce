package com.live_commerce.events.inventory;

import java.util.UUID;

/**
 * 재고 소진 이벤트 (Product → 전체)
 * Producer: product-service
 * Consumer: 재고 소진 알림 구독 서비스
 * Topic: inventory-sold-out
 */
public record InventorySoldOutEvent(
    UUID productId
) {
    public static InventorySoldOutEvent of(UUID productId) {
        return new InventorySoldOutEvent(productId);
    }
}
