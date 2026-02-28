package com.live_commerce.product.inventory.application.service;


import com.live_commerce.product.inventory.application.dto.request.InventoryCreateRequestDto;
import com.live_commerce.product.inventory.application.dto.response.InventoryCheckQuantityResponseDto;
import com.live_commerce.product.inventory.application.dto.response.InventoryCheckOrderableResponseDto;
import com.live_commerce.product.inventory.application.dto.response.InventoryResponseDto;
import com.live_commerce.product.inventory.application.mapper.InventoryMapper;
import com.live_commerce.product.inventory.application.validation.InventoryValidator;
import com.live_commerce.product.inventory.domain.exception.InventoryException;
import com.live_commerce.product.inventory.domain.model.Inventory;
import com.live_commerce.product.inventory.domain.model.InventoryStatus;
import com.live_commerce.product.inventory.domain.repository.InventoryRepository;
import com.live_commerce.events.inventory.InventoryDecreasedEvent;
import com.live_commerce.events.inventory.InventorySoldOutEvent;
import com.live_commerce.product.product.domain.repository.ProductRepository;
import com.live_commerce.product.inventory.infrastructure.redisson.DistributedLock;
import com.live_commerce.product.product.infrastructure.outbox.OutboxEventHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final InventoryValidator inventoryValidator;
    private final OutboxEventHelper outboxEventHelper;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public InventoryResponseDto createInventory(InventoryCreateRequestDto requestDto) {
        if (!productRepository.existsByProductIdAndDeletedStatusFalse(requestDto.productId())) {
            throw InventoryException.forProductNotFound();
        }

        Inventory inventory = InventoryMapper.createDtoToEntity(requestDto);
        inventoryRepository.save(inventory);
        return InventoryMapper.entityToDto(inventory);
    }

    @Transactional(readOnly = true)
    public InventoryResponseDto getInventory(UUID id) {
        Inventory inventory = inventoryRepository.findByInventoryIdAndDeletedStatusFalse(id).orElse(null);

        if (inventory == null) {
            throw InventoryException.forInventoryNotFound();
        }

        return InventoryMapper.entityToDto(inventory);
    }

    @DistributedLock(key = "#productId")
    @Transactional
    public void decreaseInventory(UUID productId, int quantity) {
        int updated = inventoryRepository.decreaseInventoryAtomically(productId, quantity);
        if (updated == 0) {
            throw InventoryException.forInventoryOutOfStock();
        }
    }

    @DistributedLock(key = "#productId")
    @Transactional
    public void increaseInventory(UUID productId, int quantity) {
        int updated = inventoryRepository.increaseInventoryAtomically(productId, quantity);
        if (updated == 0) {
            throw InventoryException.forInventoryNotFound();
        }
    }

    @DistributedLock(key = "#productId")
    @Transactional
    public void increaseInventoryV2(UUID productId, int quantity) {
        int updated = inventoryRepository.increaseInventoryAtomically(productId, quantity);
        if (updated == 0) {
            throw InventoryException.forInventoryNotFound();
        }
    }

    @Transactional
    @DistributedLock(key = "#productId")
    public void decreaseInventoryV2(UUID orderId, UUID productId, int quantity) {
        int updated = inventoryRepository.decreaseInventoryAtomically(productId, quantity);
        if (updated == 0) {
            throw InventoryException.forInventoryOutOfStock();
        }

        String soldCountKey = "product:sold_count:" + productId;
        redisTemplate.opsForValue().increment(soldCountKey, quantity);

        Inventory inventory = inventoryValidator.validateAndGetActiveInventory(productId);

        if (inventory.getAvailableQuantity() == 0) {
            inventory.changeStatus(InventoryStatus.OUT_OF_STOCK);
            InventorySoldOutEvent soldOutEvent = InventorySoldOutEvent.of(productId);
            outboxEventHelper.saveEvent("INVENTORY", productId,
                    "INVENTORY_SOLD_OUT", "inventory-sold-out", soldOutEvent);
            log.info("[InventoryService] inventory-sold-out Outbox 저장 - productId: {}", productId);
        }

        InventoryDecreasedEvent decreasedEvent = InventoryDecreasedEvent.of(orderId, productId, quantity);
        outboxEventHelper.saveEvent("INVENTORY", orderId,
                "INVENTORY_DECREASED", "inventory-decreased", decreasedEvent);
        log.info("[InventoryService] inventory-decreased Outbox 저장 - orderId: {}", orderId);
    }

    public boolean isSoldOut(UUID productId) {
        Inventory inventory = inventoryRepository.findByProductIdAndDeletedStatusFalse(productId)
                .orElse(null);

        if (inventory == null) return true;

        return inventory.getQuantity() <= 0;
    }


    public InventoryCheckQuantityResponseDto checkInventoryQuantity(UUID productId) {
        Inventory inventory = inventoryValidator.validateAndGetActiveInventory(productId);
        return InventoryMapper.toCheckQuantityDto(inventory);
    }

    public InventoryCheckOrderableResponseDto checkOrderableInventory(UUID productId, int orderQuantity) {
        boolean orderable = inventoryValidator.checkOrderable(productId, orderQuantity);
        return InventoryMapper.toCheckResponseDto(orderable);
    }

    @DistributedLock(key = "#productId", waitTime = 1, leaseTime = 2) // 강제 제한
    public void lockForTesting(UUID productId, int holdSeconds) {
        try {
            Thread.sleep(holdSeconds * 1000L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


}
