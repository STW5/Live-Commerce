package com.live_commerce.product.inventory.domain.port.in;

import com.live_commerce.product.inventory.application.dto.result.InventoryResult;

import java.util.UUID;

public interface GetInventoryUseCase {
    InventoryResult getInventory(UUID inventoryId);
}
