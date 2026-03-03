package com.live_commerce.product.inventory.application.service;

import com.live_commerce.product.inventory.domain.exception.InventoryException;
import com.live_commerce.product.inventory.domain.model.Inventory;
import com.live_commerce.product.inventory.domain.model.InventoryStatus;
import com.live_commerce.product.inventory.domain.port.in.DecreaseInventoryV2UseCase;
import com.live_commerce.product.inventory.domain.port.out.InventoryCachePort;
import com.live_commerce.product.inventory.domain.port.out.InventoryEventPublisherPort;
import com.live_commerce.product.inventory.domain.port.out.InventoryRepositoryPort;
import com.live_commerce.product.inventory.infrastructure.redisson.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DecreaseInventoryV2Service implements DecreaseInventoryV2UseCase {

    private final InventoryRepositoryPort inventoryRepositoryPort;
    private final InventoryCachePort inventoryCachePort;
    private final InventoryEventPublisherPort inventoryEventPublisherPort;

    @DistributedLock(key = "#productId")
    @Transactional
    @Override
    public void decreaseInventoryV2(UUID orderId, UUID productId, int quantity) {
        int updated = inventoryRepositoryPort.decreaseInventoryAtomically(productId, quantity);
        if (updated == 0) {
            throw InventoryException.forInventoryOutOfStock();
        }

        inventoryCachePort.incrementSoldCount(productId, quantity);

        Inventory inventory = inventoryRepositoryPort.findByProductId(productId)
                .orElseThrow(InventoryException::forInventoryNotFound);

        if (inventory.getAvailableQuantity() == 0) {
            inventory.changeStatus(InventoryStatus.OUT_OF_STOCK);
            inventoryRepositoryPort.save(inventory);
            inventoryEventPublisherPort.publishInventorySoldOut(productId);
            log.info("[DecreaseInventoryV2Service] inventory-sold-out Outbox 저장 - productId: {}", productId);
        }

        inventoryEventPublisherPort.publishInventoryDecreased(orderId, productId, quantity);
        log.info("[DecreaseInventoryV2Service] inventory-decreased Outbox 저장 - orderId: {}", orderId);
    }
}
