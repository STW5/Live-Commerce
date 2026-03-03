package com.live_commerce.product.inventory.domain.port.in;

import java.util.UUID;

public interface IncreaseInventoryV2UseCase {
    void increaseInventoryV2(UUID productId, int quantity);
}
