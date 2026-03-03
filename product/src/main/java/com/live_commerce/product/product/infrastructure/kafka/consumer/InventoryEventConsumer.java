package com.live_commerce.product.product.infrastructure.kafka.consumer;

import com.live_commerce.product.inventory.domain.exception.InventoryException;
import com.live_commerce.product.inventory.domain.port.in.DecreaseInventoryV2UseCase;
import com.live_commerce.product.inventory.domain.port.in.IncreaseInventoryV2UseCase;
import com.live_commerce.events.inventory.InventoryRollbackEvent;
import com.live_commerce.events.inventory.InventoryDecreaseRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class InventoryEventConsumer {

    private final DecreaseInventoryV2UseCase decreaseInventoryV2UseCase;
    private final IncreaseInventoryV2UseCase increaseInventoryV2UseCase;

    @KafkaListener(topics = "inventory-decrease", concurrency = "4", containerFactory = "kafkaListenerContainerFactory")
    public void consumeOrderCreated(InventoryDecreaseRequestEvent event) {
        log.info("inventory-decrease 이벤트 수신: {}", event);

        try {
            decreaseInventoryV2UseCase.decreaseInventoryV2(event.orderId(), event.productId(), event.quantity());
            log.info("[InventoryEventConsumer] 재고 차감 및 Outbox 저장 완료 - orderId: {}", event.orderId());
        } catch (InventoryException e) {
            log.error("재고 차감 실패: {}, 이유: {}", event.orderId(), e.getMessage());
        }
    }

    @KafkaListener(topics = "inventory-rollback")
    public void consumeInventoryRollback(InventoryRollbackEvent event) {
        log.info("inventory-rollback 이벤트 수신: {}", event);

        try {
            increaseInventoryV2UseCase.increaseInventoryV2(event.productId(), event.quantity());
            log.info("재고 복구 완료 - productId: {}, quantity: {}", event.productId(), event.quantity());
        } catch (Exception e) {
            log.error("재고 복구 실패 - productId: {}, 이유: {}", event.productId(), e.getMessage());
        }
    }
}
