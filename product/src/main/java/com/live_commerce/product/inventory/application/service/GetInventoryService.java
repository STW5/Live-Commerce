package com.live_commerce.product.inventory.application.service;

import com.live_commerce.product.inventory.application.dto.result.InventoryResult;
import com.live_commerce.product.inventory.domain.exception.InventoryException;
import com.live_commerce.product.inventory.domain.model.Inventory;
import com.live_commerce.product.inventory.domain.port.in.GetInventoryUseCase;
import com.live_commerce.product.inventory.domain.port.out.InventoryRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetInventoryService implements GetInventoryUseCase {

    private final InventoryRepositoryPort inventoryRepositoryPort;

    @Transactional(readOnly = true)
    @Override
    public InventoryResult getInventory(UUID inventoryId) {
        Inventory inventory = inventoryRepositoryPort.findByInventoryId(inventoryId)
                .orElseThrow(InventoryException::forInventoryNotFound);
        return InventoryResult.from(inventory);
    }
}
