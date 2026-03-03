package com.live_commerce.product.inventory.application.dto.result;

import com.live_commerce.product.inventory.domain.model.Inventory;
import com.live_commerce.product.inventory.domain.model.InventoryStatus;

import java.util.UUID;

public record InventoryResult(
        UUID inventoryId,
        UUID productId,
        Integer quantity,
        Integer reservedQuantity,
        Integer availableQuantity,
        InventoryStatus inventoryStatus
) {
    public static InventoryResult from(Inventory inventory) {
        return new InventoryResult(
                inventory.getInventoryId(),
                inventory.getProductId(),
                inventory.getQuantity(),
                inventory.getReservedQuantity(),
                inventory.getAvailableQuantity(),
                inventory.getInventoryStatus()
        );
    }
}
