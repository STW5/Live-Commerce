package com.live_commerce.product.inventory.application.service;

import com.live_commerce.product.inventory.domain.exception.InventoryException;
import com.live_commerce.product.inventory.domain.port.in.IncreaseInventoryV2UseCase;
import com.live_commerce.product.inventory.domain.port.out.InventoryRepositoryPort;
import com.live_commerce.product.inventory.infrastructure.redisson.DistributedLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IncreaseInventoryV2Service implements IncreaseInventoryV2UseCase {

    private final InventoryRepositoryPort inventoryRepositoryPort;

    @DistributedLock(key = "#productId")
    @Transactional
    @Override
    public void increaseInventoryV2(UUID productId, int quantity) {
        int updated = inventoryRepositoryPort.increaseInventoryAtomically(productId, quantity);
        if (updated == 0) {
            throw InventoryException.forInventoryNotFound();
        }
    }
}
