package com.live_commerce.product.inventory.application.service;

import com.live_commerce.product.inventory.application.dto.result.InventoryOrderableResult;
import com.live_commerce.product.inventory.domain.exception.InventoryException;
import com.live_commerce.product.inventory.domain.port.in.CheckOrderableInventoryUseCase;
import com.live_commerce.product.inventory.domain.port.out.InventoryRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckOrderableInventoryService implements CheckOrderableInventoryUseCase {

    private final InventoryRepositoryPort inventoryRepositoryPort;

    @Transactional(readOnly = true)
    @Override
    public InventoryOrderableResult checkOrderableInventory(UUID productId, int orderQuantity) {
        boolean exists = inventoryRepositoryPort.findByProductId(productId).isPresent();
        if (!exists) {
            throw InventoryException.forInventoryNotFound();
        }
        boolean orderable = inventoryRepositoryPort.existsOrderableInventory(productId, orderQuantity);
        return new InventoryOrderableResult(orderable);
    }
}
