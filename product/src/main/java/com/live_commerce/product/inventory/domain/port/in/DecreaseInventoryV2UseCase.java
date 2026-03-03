package com.live_commerce.product.inventory.domain.port.in;

import java.util.UUID;

public interface DecreaseInventoryV2UseCase {
    void decreaseInventoryV2(UUID orderId, UUID productId, int quantity);
}
