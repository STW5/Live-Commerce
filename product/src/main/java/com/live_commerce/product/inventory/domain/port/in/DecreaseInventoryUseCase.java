package com.live_commerce.product.inventory.domain.port.in;

import java.util.UUID;

public interface DecreaseInventoryUseCase {
    void decreaseInventory(UUID productId, int quantity);
}
