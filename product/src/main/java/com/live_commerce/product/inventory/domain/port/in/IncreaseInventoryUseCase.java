package com.live_commerce.product.inventory.domain.port.in;

import java.util.UUID;

public interface IncreaseInventoryUseCase {
    void increaseInventory(UUID productId, int quantity);
}
