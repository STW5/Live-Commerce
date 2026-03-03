package com.live_commerce.product.inventory.domain.port.in;

import com.live_commerce.product.inventory.application.dto.result.InventoryOrderableResult;

import java.util.UUID;

public interface CheckOrderableInventoryUseCase {
    InventoryOrderableResult checkOrderableInventory(UUID productId, int orderQuantity);
}
