package com.live_commerce.product.inventory.domain.port.out;

import com.live_commerce.product.inventory.domain.model.Inventory;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepositoryPort {
    Inventory save(Inventory inventory);
    Optional<Inventory> findByInventoryId(UUID inventoryId);
    Optional<Inventory> findByProductId(UUID productId);
    boolean existsOrderableInventory(UUID productId, int quantity);
    int decreaseInventoryAtomically(UUID productId, int quantity);
    int increaseInventoryAtomically(UUID productId, int quantity);
}
