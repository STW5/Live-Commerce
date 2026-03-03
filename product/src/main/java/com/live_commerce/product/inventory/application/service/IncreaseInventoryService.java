package com.live_commerce.product.inventory.application.service;

import com.live_commerce.product.inventory.domain.exception.InventoryException;
import com.live_commerce.product.inventory.domain.port.in.IncreaseInventoryUseCase;
import com.live_commerce.product.inventory.domain.port.out.InventoryRepositoryPort;
import com.live_commerce.product.inventory.infrastructure.redisson.DistributedLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IncreaseInventoryService implements IncreaseInventoryUseCase {

    private final InventoryRepositoryPort inventoryRepositoryPort;

    @DistributedLock(key = "#productId")
    @Transactional
    @Override
    public void increaseInventory(UUID productId, int quantity) {
        int updated = inventoryRepositoryPort.increaseInventoryAtomically(productId, quantity);
        if (updated == 0) {
            throw InventoryException.forInventoryNotFound();
        }
    }
}
