package com.live_commerce.product.inventory.application.service;

import com.live_commerce.product.inventory.application.dto.result.InventoryQuantityResult;
import com.live_commerce.product.inventory.domain.exception.InventoryException;
import com.live_commerce.product.inventory.domain.model.Inventory;
import com.live_commerce.product.inventory.domain.port.in.CheckInventoryQuantityUseCase;
import com.live_commerce.product.inventory.domain.port.out.InventoryRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckInventoryQuantityService implements CheckInventoryQuantityUseCase {

    private final InventoryRepositoryPort inventoryRepositoryPort;

    @Transactional(readOnly = true)
    @Override
    public InventoryQuantityResult checkInventoryQuantity(UUID productId) {
        Inventory inventory = inventoryRepositoryPort.findByProductId(productId)
                .orElseThrow(InventoryException::forInventoryNotFound);
        return new InventoryQuantityResult(inventory.getAvailableQuantity());
    }
}
