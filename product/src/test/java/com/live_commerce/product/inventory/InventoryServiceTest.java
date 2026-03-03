package com.live_commerce.product.inventory;

import com.live_commerce.product.inventory.adapter.out.persistence.InventoryJpaRepository;
import com.live_commerce.product.inventory.domain.exception.InventoryException;
import com.live_commerce.product.inventory.domain.model.Inventory;
import com.live_commerce.product.inventory.domain.model.InventoryStatus;
import com.live_commerce.product.inventory.domain.port.in.DecreaseInventoryUseCase;
import com.live_commerce.product.inventory.domain.port.out.InventoryRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;


@SpringBootTest
@ActiveProfiles("test")
public class InventoryServiceTest {

    private static final UUID PRODUCT_ID = UUID.fromString("a8e5b7f9-bc53-4b3d-a2d2-d2513fa44b57");
    private static final int INITIAL_QUANTITY = 100;

    @Autowired
    private DecreaseInventoryUseCase decreaseInventoryUseCase;

    @Autowired
    private InventoryRepositoryPort inventoryRepositoryPort;

    @Autowired
    private InventoryJpaRepository inventoryJpaRepository;

    @BeforeEach
    @Rollback(false)
    void setUp() {
        if (inventoryRepositoryPort.findByProductId(PRODUCT_ID).isEmpty()) {
            Inventory inventory = Inventory.create(
                    PRODUCT_ID,
                    INITIAL_QUANTITY,
                    0,
                    INITIAL_QUANTITY,
                    InventoryStatus.AVAILABLE
            );
            inventoryRepositoryPort.save(inventory);
        }
    }

    @Test
    void 동시_재고감소_테스트() throws InterruptedException {

        System.out.println("=== BEFORE TEST ===");
        inventoryJpaRepository.findAll().forEach(i ->
                System.out.println("재고 있음: " + i.getProductId() + ", 삭제 상태: " + i.isDeletedStatus()));

        int threadCount = 10;
        int decreaseAmountPerThread = 1;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    decreaseInventoryUseCase.decreaseInventory(PRODUCT_ID, decreaseAmountPerThread);
                } catch (Exception e) {
                    System.out.println("예외 발생: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        Inventory inventory = inventoryRepositoryPort.findByProductId(PRODUCT_ID)
                .orElseThrow(() -> new RuntimeException("재고가 존재하지 않습니다."));

        assertEquals(
                INITIAL_QUANTITY - (threadCount * decreaseAmountPerThread),
                inventory.getAvailableQuantity()
        );
    }

    @Test
    void 재고초과요청시_예외발생_테스트() throws InterruptedException {
        System.out.println("=== BEFORE OVER-REQUEST TEST ===");
        inventoryJpaRepository.findAll().forEach(i ->
                System.out.println("재고 있음: " + i.getProductId() + ", 삭제 상태: " + i.isDeletedStatus()));

        int threadCount = 150;
        int decreaseAmountPerThread = 1;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    decreaseInventoryUseCase.decreaseInventory(PRODUCT_ID, decreaseAmountPerThread);
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("예외 발생: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        Inventory inventory = inventoryRepositoryPort.findByProductId(PRODUCT_ID)
                .orElseThrow(() -> new RuntimeException("재고가 존재하지 않습니다."));

        System.out.println("최종 재고: " + inventory.getAvailableQuantity());
        System.out.println("실패 요청 수: " + failCount.get());

        assertEquals(0, inventory.getAvailableQuantity());
        assertEquals(threadCount - INITIAL_QUANTITY, failCount.get());
    }
}
