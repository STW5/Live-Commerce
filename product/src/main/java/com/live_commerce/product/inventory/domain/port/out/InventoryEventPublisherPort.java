package com.live_commerce.product.inventory.domain.port.out;

import java.util.UUID;

public interface InventoryEventPublisherPort {
    void publishInventoryDecreased(UUID orderId, UUID productId, int quantity);
    void publishInventorySoldOut(UUID productId);
}
