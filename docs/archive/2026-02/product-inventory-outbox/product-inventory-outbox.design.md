# Design: product-inventory-outbox

> Product 서비스 inventory 이벤트 발행을 Outbox 패턴으로 전환

**작성일**: 2026-02-28
**참조 Plan**: `docs/01-plan/features/product-inventory-outbox.plan.md`
**영향 모듈**: `product`

---

## 1. As-Is 현황

### 1.1 문제 흐름

```
Order 서비스                         Product 서비스
    │                                    │
    │──[inventory-decrease, Outbox]──────▶│
    │                                    │
    │                         @DistributedLock
    │                         @Transactional
    │                         decreaseInventoryV2()
    │                           ├─ DB: 재고 차감
    │                           ├─ Redis: sold_count++
    │                           └─ kafkaTemplate.send("inventory-sold-out") ⚠️
    │                                    │ 트랜잭션 커밋
    │                                    │
    │                         kafkaTemplate.send("inventory-decreased") ⚠️
    │◀──[inventory-decreased, 직접 Kafka]─│
```

**⚠️ 위험 지점 2곳:**
1. `inventory-sold-out`: `@Transactional` 내부에서 직접 Kafka 호출 (트랜잭션 커밋 전 발행)
2. `inventory-decreased`: 트랜잭션 커밋 후 별도 Kafka 호출 (커밋 성공 → 발행 실패 시 유실)

### 1.2 현재 파일 구조

**InventoryService.java** (변경 대상)
```java
@Service
@RequiredArgsConstructor
public class InventoryService {
    private final KafkaTemplate<String, Object> kafkaTemplate;  // ← 제거 예정
    ...

    @Transactional
    @DistributedLock(key = "#productId")
    public void decreaseInventoryV2(UUID productId, int quantity) {
        // DB 차감
        // Redis sold_count 업데이트
        if (inventory.getAvailableQuantity() == 0) {
            kafkaTemplate.send("inventory-sold-out", soldOutEvent);  // ⚠️ 직접 호출
        }
    }
}
```

**InventoryEventConsumer.java** (변경 대상)
```java
@Service
@RequiredArgsConstructor
public class InventoryEventConsumer {
    private final InventoryService inventoryService;
    private final KafkaTemplate<String, Object> kafkaTemplate;  // ← 제거 예정

    @KafkaListener(topics = "inventory-decrease", ...)
    public void consumeOrderCreated(InventoryDecreaseRequestEvent event) {
        inventoryService.decreaseInventoryV2(event.productId(), event.quantity());

        kafkaTemplate.send("inventory-decreased", ...);  // ⚠️ 트랜잭션 밖에서 직접 호출
    }
}
```

---

## 2. To-Be 설계

### 2.1 핵심 전략: decreaseInventoryV2에 orderId 추가, 모든 Outbox 저장을 단일 트랜잭션으로

**AOP 실행 순서 분석:**

```
[DistributedLockAspect] - 커스텀 Aspect (기본 Order, @Transactional보다 우선)
  └─ Redisson Lock 획득
     └─ [Spring @Transactional Proxy]
          └─ DB Transaction 시작
             └─ decreaseInventoryV2() 실제 실행
                  ├─ 재고 차감 (DB)
                  ├─ sold_count++ (Redis)
                  ├─ outboxHelper.saveEvent("INVENTORY_SOLD_OUT")  ← 트랜잭션 내
                  └─ outboxHelper.saveEvent("INVENTORY_DECREASED") ← 트랜잭션 내
             └─ DB Transaction 커밋 (재고 차감 + Outbox 이벤트 원자적 저장)
  └─ Redisson Lock 해제
```

두 Outbox 이벤트가 재고 차감과 **동일 트랜잭션 내**에서 저장되어 원자성 보장.

### 2.2 변경 전체 요약

| 파일 | 변경 유형 | 핵심 변경 |
|------|----------|----------|
| `product/build.gradle` | 수정 | `common-lib` 의존성 추가 |
| `product/.../outbox/OutboxEventHelper.java` | 신규 생성 | Order 서비스와 동일 패턴 |
| `product/.../service/InventoryService.java` | 수정 | `orderId` 파라미터 추가, Outbox 저장으로 전환 |
| `product/.../kafka/consumer/InventoryEventConsumer.java` | 수정 | `orderId` 전달, kafkaTemplate 제거 |

---

## 3. 컴포넌트별 변경 명세

### 3.1 product/build.gradle

```groovy
// 기존 마지막 줄
implementation project(':event-schema')

// 추가
// common-lib (Outbox Pattern)
implementation project(':common-lib')
```

---

### 3.2 신규: OutboxEventHelper.java

**경로**: `product/src/main/java/com/live_commerce/product/product/infrastructure/outbox/OutboxEventHelper.java`

Order 서비스의 동일 파일을 product 패키지로 복제한다.

```java
package com.live_commerce.product.product.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.live_commerce.common.outbox.OutboxEvent;
import com.live_commerce.common.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Outbox 이벤트 저장 헬퍼
 * - 호출자의 @Transactional 컨텍스트에 참여하여 원자적 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventHelper {

    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public void saveEvent(String aggregateType, UUID aggregateId,
                          String eventType, String topic, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            OutboxEvent outboxEvent = OutboxEvent.create(aggregateType, aggregateId, eventType, topic, json);
            outboxEventRepository.save(outboxEvent);
            log.debug("[OutboxHelper] 이벤트 저장 - type: {}, aggregateId: {}, topic: {}",
                    eventType, aggregateId, topic);
        } catch (JsonProcessingException e) {
            log.error("[OutboxHelper] 이벤트 직렬화 실패 - type: {}, error: {}", eventType, e.getMessage());
            throw new RuntimeException("Outbox 이벤트 직렬화 실패: " + eventType, e);
        }
    }
}
```

---

### 3.3 InventoryService.java

**경로**: `product/src/main/java/com/live_commerce/product/inventory/application/service/InventoryService.java`

**Before:**
```java
@Service
@RequiredArgsConstructor
public class InventoryService {
    ...
    private final KafkaTemplate<String, Object> kafkaTemplate;
    ...

    @Transactional
    @DistributedLock(key = "#productId")
    public void decreaseInventoryV2(UUID productId, int quantity) {
        int updated = inventoryRepository.decreaseInventoryAtomically(productId, quantity);
        if (updated == 0) throw InventoryException.forInventoryOutOfStock();

        String soldCountKey = "product:sold_count:" + productId;
        redisTemplate.opsForValue().increment(soldCountKey, quantity);

        Inventory inventory = inventoryValidator.validateAndGetActiveInventory(productId);
        if (inventory.getAvailableQuantity() == 0) {
            inventory.changeStatus(InventoryStatus.OUT_OF_STOCK);
            InventorySoldOutEvent soldOutEvent = new InventorySoldOutEvent(productId);
            kafkaTemplate.send("inventory-sold-out", soldOutEvent);      // ← 제거
            log.info("inventory-sold-out 이벤트 발행 완료: {}", soldOutEvent);
        }
    }
}
```

**After:**
```java
@Service
@RequiredArgsConstructor
public class InventoryService {
    ...
    // KafkaTemplate 제거
    private final OutboxEventHelper outboxEventHelper;                  // ← 추가
    ...

    @Transactional
    @DistributedLock(key = "#productId")
    public void decreaseInventoryV2(UUID orderId, UUID productId, int quantity) {  // orderId 추가
        int updated = inventoryRepository.decreaseInventoryAtomically(productId, quantity);
        if (updated == 0) throw InventoryException.forInventoryOutOfStock();

        String soldCountKey = "product:sold_count:" + productId;
        redisTemplate.opsForValue().increment(soldCountKey, quantity);

        Inventory inventory = inventoryValidator.validateAndGetActiveInventory(productId);
        if (inventory.getAvailableQuantity() == 0) {
            inventory.changeStatus(InventoryStatus.OUT_OF_STOCK);
            InventorySoldOutEvent soldOutEvent = InventorySoldOutEvent.of(productId);
            outboxEventHelper.saveEvent("INVENTORY", productId,         // ← Outbox로 전환
                    "INVENTORY_SOLD_OUT", "inventory-sold-out", soldOutEvent);
            log.info("[InventoryService] inventory-sold-out Outbox 저장 - productId: {}", productId);
        }

        // inventory-decreased도 동일 트랜잭션 내에서 Outbox 저장
        InventoryDecreasedEvent decreasedEvent = InventoryDecreasedEvent.of(orderId, productId, quantity);
        outboxEventHelper.saveEvent("INVENTORY", orderId,              // ← Outbox로 전환
                "INVENTORY_DECREASED", "inventory-decreased", decreasedEvent);
        log.info("[InventoryService] inventory-decreased Outbox 저장 - orderId: {}", orderId);
    }
}
```

**변경 요약:**
- `KafkaTemplate` 필드 제거
- `OutboxEventHelper` 필드 추가
- 시그니처에 `UUID orderId` 파라미터 추가
- `kafkaTemplate.send("inventory-sold-out", ...)` → `outboxEventHelper.saveEvent(...)` 로 교체
- `inventory-decreased` Outbox 저장 추가 (기존에는 consumer에서 발행)

---

### 3.4 InventoryEventConsumer.java

**경로**: `product/src/main/java/com/live_commerce/product/product/infrastructure/kafka/consumer/InventoryEventConsumer.java`

**Before:**
```java
@Service
@RequiredArgsConstructor
public class InventoryEventConsumer {
    private final InventoryService inventoryService;
    private final KafkaTemplate<String, Object> kafkaTemplate;      // ← 제거

    @KafkaListener(topics = "inventory-decrease", concurrency = "4",
                   containerFactory = "kafkaListenerContainerFactory")
    public void consumeOrderCreated(InventoryDecreaseRequestEvent event) {
        log.info("inventory-decrease 이벤트 수신: {}", event);
        try {
            inventoryService.decreaseInventoryV2(event.productId(), event.quantity());

            InventoryDecreasedEvent decreasedEvent = InventoryDecreasedEvent.of(
                    event.orderId(), event.productId(), event.quantity());
            kafkaTemplate.send("inventory-decreased",                 // ← 제거
                    event.productId().toString(), decreasedEvent);
            log.info("inventory-decreased 이벤트 발행 완료: {}", decreasedEvent);
        } catch (InventoryException e) {
            log.error("재고 차감 실패: {}, 이유: {}", event.orderId(), e.getMessage());
        }
    }
    ...
}
```

**After:**
```java
@Service
@RequiredArgsConstructor
public class InventoryEventConsumer {
    private final InventoryService inventoryService;
    // KafkaTemplate 제거

    @KafkaListener(topics = "inventory-decrease", concurrency = "4",
                   containerFactory = "kafkaListenerContainerFactory")
    public void consumeOrderCreated(InventoryDecreaseRequestEvent event) {
        log.info("inventory-decrease 이벤트 수신: {}", event);
        try {
            // orderId 추가 전달 - 내부에서 모든 Outbox 저장 원자적 처리
            inventoryService.decreaseInventoryV2(                     // orderId 추가
                    event.orderId(), event.productId(), event.quantity());
            log.info("[InventoryEventConsumer] 재고 차감 및 Outbox 저장 완료 - orderId: {}",
                    event.orderId());
        } catch (InventoryException e) {
            log.error("재고 차감 실패: {}, 이유: {}", event.orderId(), e.getMessage());
        }
    }
    ...
}
```

**변경 요약:**
- `KafkaTemplate` 필드 제거
- `InventoryDecreasedEvent` 생성 및 `kafkaTemplate.send()` 블록 제거
- `decreaseInventoryV2(orderId, productId, quantity)` — orderId 추가 전달

---

## 4. ADR (Architecture Decision Records)

### ADR-1: inventory-decreased를 InventoryService 내부로 이동

**결정**: `inventory-decreased` Outbox 저장을 `consumeOrderCreated()`에서 `decreaseInventoryV2()`로 이동

**이유**:
- `@DistributedLock + @Transactional` 단일 원자 단위 내에서 재고 차감 + 두 이벤트를 동시에 처리
- consumer에 `@Transactional`을 추가하면 `@KafkaListener`와의 트랜잭션 전파가 복잡해짐
- 서비스 메서드 하나가 단일 책임을 유지

**대안**: `consumeOrderCreated()`에 `@Transactional` 추가 → Kafka Consumer와 DB 트랜잭션 혼용, AOP 프록시 중첩 복잡성

### ADR-2: OutboxEventHelper를 product 패키지에 별도 생성

**결정**: `product.product.infrastructure.outbox.OutboxEventHelper` 신규 생성

**이유**:
- `OutboxEventHelper`는 현재 각 서비스 패키지에 위치 (order 서비스 패턴 일치)
- common-lib으로 이동 시 Jackson ObjectMapper 의존성 및 서비스별 customization 어려움
- 기존 order 서비스 패턴 유지

**대안**: common-lib으로 OutboxEventHelper 이동 → 더 큰 변경 범위, 별도 기능으로 다룰 것

### ADR-3: InventorySoldOutEvent.of() 정적 팩토리 사용

**결정**: `new InventorySoldOutEvent(productId)` → `InventorySoldOutEvent.of(productId)`

**이유**:
- event-schema의 다른 이벤트와 일관성 유지 (`InventoryDecreasedEvent.of(...)` 패턴)
- InventorySoldOutEvent에 이미 `of()` 정적 팩토리 존재

---

## 5. 구현 순서

```
Step 1. product/build.gradle → common-lib 의존성 추가
Step 2. OutboxEventHelper.java 신규 생성
         (product.product.infrastructure.outbox 패키지)
Step 3. InventoryService.java 수정
         - KafkaTemplate 필드 제거
         - OutboxEventHelper 필드 추가
         - decreaseInventoryV2() 시그니처에 orderId 추가
         - kafkaTemplate.send() → outboxEventHelper.saveEvent() 교체
         - inventory-decreased Outbox 저장 추가
Step 4. InventoryEventConsumer.java 수정
         - KafkaTemplate 필드 제거
         - decreaseInventoryV2() 호출에 orderId 추가
         - kafkaTemplate.send("inventory-decreased", ...) 블록 제거
Step 5. ./gradlew :product:compileJava 빌드 확인
```

---

## 6. 완료 기준 (체크리스트)

- [ ] `product/build.gradle`에 `common-lib` 의존성 추가
- [ ] `OutboxEventHelper.java` 생성 (`product.product.infrastructure.outbox` 패키지)
- [ ] `InventoryService.decreaseInventoryV2()`의 `KafkaTemplate` 직접 호출 제거
- [ ] `InventoryService.decreaseInventoryV2()` 시그니처에 `orderId` 파라미터 추가
- [ ] `inventory-decreased` Outbox 저장이 `decreaseInventoryV2()` 내부에서 수행
- [ ] `inventory-sold-out` Outbox 저장이 `decreaseInventoryV2()` 내부에서 수행
- [ ] `InventoryEventConsumer`의 `KafkaTemplate` 의존성 제거
- [ ] `./gradlew :product:compileJava` 성공
