package com.live_commerce.product.inventory.adapter.out.persistence;

import com.live_commerce.product.inventory.domain.model.Inventory;
import com.live_commerce.product.inventory.domain.port.out.InventoryRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InventoryPersistenceAdapter implements InventoryRepositoryPort {

    private final InventoryJpaRepository inventoryJpaRepository;

    @Override
    public Inventory save(Inventory inventory) {
        InventoryJpaEntity entity = InventoryJpaEntity.from(inventory);
        return inventoryJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Inventory> findByInventoryId(UUID inventoryId) {
        return inventoryJpaRepository.findByInventoryIdAndDeletedStatusFalse(inventoryId)
                .map(InventoryJpaEntity::toDomain);
    }

    @Override
    public Optional<Inventory> findByProductId(UUID productId) {
        return inventoryJpaRepository.findByProductIdAndDeletedStatusFalse(productId)
                .map(InventoryJpaEntity::toDomain);
    }

    @Override
    public boolean existsOrderableInventory(UUID productId, int quantity) {
        return inventoryJpaRepository.existsOrderableInventory(productId, quantity);
    }

    @Override
    public int decreaseInventoryAtomically(UUID productId, int quantity) {
        return inventoryJpaRepository.decreaseInventoryAtomically(productId, quantity);
    }

    @Override
    public int increaseInventoryAtomically(UUID productId, int quantity) {
        return inventoryJpaRepository.increaseInventoryAtomically(productId, quantity);
    }
}
