package com.live_commerce.product.product.infrastructure.kafka.consumer;

import com.live_commerce.events.inventory.InventorySoldOutEvent;
import com.live_commerce.product.product.domain.exception.ProductException;
import com.live_commerce.product.product.domain.model.Product;
import com.live_commerce.product.product.domain.model.ProductStatus;
import com.live_commerce.product.product.domain.port.out.ProductRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class ProductSoldOutListener {

    private final ProductRepositoryPort productRepositoryPort;

    @Transactional
    @KafkaListener(topics = "inventory-sold-out")
    public void consumeSoldOut(InventorySoldOutEvent event) {
        log.info("inventory-sold-out 이벤트 수신: {}", event);

        Product product = productRepositoryPort.findById(event.productId())
                .orElseThrow(ProductException::forProductNotFound);

        product.changeStatus(ProductStatus.SOLD_OUT);
        productRepositoryPort.save(product);
    }
}
