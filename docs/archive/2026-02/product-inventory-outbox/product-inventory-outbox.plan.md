# Plan: product-inventory-outbox

## 개요

| 항목 | 내용 |
|------|------|
| Feature | product-inventory-outbox |
| 작성일 | 2026-02-28 |
| 우선순위 | P1 (Saga 신뢰성 완성) |
| 예상 복잡도 | 낮음 (파일 3~5개) |
| 담당 서비스 | product (port: 19070) |

## 배경 및 목적

Order 서비스의 `inventory-decrease` Outbox 전환(compensation-transaction)은 완료됐지만,
Product 서비스가 응답으로 발행하는 **`inventory-decreased`** 이벤트는 여전히 `kafkaTemplate.send()` 직접 호출 방식이다.

```
현재 흐름:
Order ─[inventory-decrease, Outbox]→ Product
Product ─[inventory-decreased, 직접kafkaTemplate]→ Order  ← ⚠️ 유실 위험
```

`kafkaTemplate.send()` 직접 호출은 DB 트랜잭션과 원자성이 보장되지 않는다:
- DB commit 성공 → Kafka 발행 실패 → Order 서비스가 응답을 못 받고 멈춤
- 재고는 차감됐지만 주문 진행이 중단되는 데이터 불일치 발생

**목표**: product 서비스의 Kafka 이벤트 발행을 Outbox 패턴으로 전환하여
전체 Saga의 이벤트 발행 신뢰성을 완성한다.

## 영향 범위

### 발행 이벤트 (변경 대상)

| 이벤트 | 현재 방식 | 변경 방식 | 위치 |
|--------|----------|----------|------|
| `inventory-decreased` | `kafkaTemplate.send()` 직접 | Outbox 저장 | `InventoryEventConsumer` |
| `inventory-sold-out` | `kafkaTemplate.send()` 직접 | Outbox 저장 | `InventoryService.decreaseInventoryV2()` |

### 수신 이벤트 (변경 없음)

| 이벤트 | 처리 위치 | 비고 |
|--------|----------|------|
| `inventory-decrease` | `InventoryEventConsumer` | 변경 없음 |
| `inventory-rollback` | `InventoryEventConsumer` | 변경 없음 |

### 파일 변경 목록

| 파일 | 변경 유형 | 내용 |
|------|----------|------|
| `product/build.gradle` | 수정 | `common-lib` 의존성 추가 |
| `product/.../inventory/application/service/InventoryService.java` | 수정 | `inventory-sold-out` → Outbox |
| `product/.../inventory/infrastructure/kafka/InventoryEventConsumer.java` | 수정 | `inventory-decreased` → Outbox |
| `product/src/test/resources/application-test.yml` | 신규/수정 | Outbox 테스트 설정 |

## 현황 분석

### As-Is: InventoryEventConsumer

```java
public void consumeOrderCreated(InventoryDecreaseRequestEvent event) {
    inventoryService.decreaseInventoryV2(event.productId(), event.quantity());

    // ⚠️ 트랜잭션 밖에서 직접 Kafka 호출
    kafkaTemplate.send("inventory-decreased", event.productId().toString(), decreasedEvent);
}
```

**문제**: `decreaseInventoryV2()` 트랜잭션 커밋 후 Kafka 발행 전에 장애 발생 시
재고는 차감됐지만 Order 서비스는 응답을 받지 못함.

### As-Is: InventoryService.decreaseInventoryV2()

```java
@DistributedLock(key = "#productId")
@Transactional
public void decreaseInventoryV2(UUID productId, int quantity) {
    inventoryRepository.decreaseInventoryAtomically(productId, quantity);
    // ...Redis sold_count 업데이트...

    if (inventory.getAvailableQuantity() == 0) {
        // ⚠️ 트랜잭션 내에서 직접 Kafka 호출 (트랜잭션 커밋 전 발행)
        kafkaTemplate.send("inventory-sold-out", soldOutEvent);
    }
}
```

## To-Be 설계 방향

### 핵심 제약: @DistributedLock + @Transactional 조합

`decreaseInventoryV2()`는 `@DistributedLock` + `@Transactional`이 중첩 적용된다.
AOP 실행 순서: **Lock 획득 → 트랜잭션 시작 → 메서드 실행 → 트랜잭션 커밋 → Lock 해제**

Outbox 이벤트는 트랜잭션 내부에 저장되어야 원자성이 보장된다.

### To-Be 방향

**`inventory-sold-out`**: `decreaseInventoryV2()` 내부 트랜잭션 안에서 OutboxEventHelper로 저장
```
@DistributedLock @Transactional decreaseInventoryV2()
  └── if (soldOut) → outboxHelper.saveEvent("INVENTORY", productId, "INVENTORY_SOLD_OUT", ...)
  └── 트랜잭션 커밋과 동시에 Outbox 저장 → 원자성 보장
```

**`inventory-decreased`**: `consumeOrderCreated()`에 `@Transactional` 추가 후 Outbox 저장
```
@Transactional consumeOrderCreated()
  ├── decreaseInventoryV2() - 내부 @Transactional이 외부와 합류 (REQUIRED 전파)
  └── outboxHelper.saveEvent("INVENTORY", orderId, "INVENTORY_DECREASED", ...)
  └── 트랜잭션 커밋 시 재고 차감 + Outbox 저장 원자적 완료
```

> **@DistributedLock 주의**: Lock은 트랜잭션 시작 전에 획득되므로, 외부 `@Transactional`로 감싸도
> Lock → 외부 트랜잭션 시작 → 내부 decreaseInventoryV2 합류 → 커밋 → Lock 해제 순서 유지.

## 완료 기준

- [ ] `product/build.gradle`에 `common-lib` 의존성 추가
- [ ] `inventory-decreased` 이벤트: Outbox 저장으로 전환 (kafkaTemplate 직접 호출 제거)
- [ ] `inventory-sold-out` 이벤트: Outbox 저장으로 전환 (kafkaTemplate 직접 호출 제거)
- [ ] `InventoryEventConsumer`의 `KafkaTemplate` 의존성 제거
- [ ] Outbox 메트릭에서 `aggregateType=INVENTORY` 이벤트 확인 가능
- [ ] `./gradlew :product:compileJava` 성공
