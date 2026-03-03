# Design: 보상 트랜잭션 (Compensation Transaction) 안정화

**Feature:** compensation-transaction
**작성일:** 2026-02-26
**PDCA 단계:** Design
**참조 Plan:** `docs/01-plan/features/compensation-transaction.plan.md`

---

## 1. 현재 구현 상태 (As-Is)

### 1-1. 이미 완료된 항목 ✅

코드베이스 탐색 결과, Plan 이후 상당 부분이 선행 구현됨:

| 항목 | 파일 | 상태 |
|------|------|------|
| OutboxEvent Entity | `common-lib/.../outbox/OutboxEvent.java` | ✅ 완전 구현 |
| OutboxEventPublisher (@Scheduled) | `common-lib/.../outbox/OutboxEventPublisher.java` | ✅ 완전 구현 |
| OutboxEventRepository | `common-lib/.../outbox/OutboxEventRepository.java` | ✅ 완전 구현 |
| SagaState Entity | `common-lib/.../saga/SagaState.java` | ✅ 완전 구현 |
| SagaStateRepository | `common-lib/.../saga/SagaStateRepository.java` | ✅ 완전 구현 |
| event-schema 이벤트 DTO 6개 | `event-schema/.../events/` | ✅ 완전 구현 |
| Order: Outbox 보상 이벤트 발행 | `KafkaOrderEventPublisher` | ✅ rollback, order-failed |
| Order: 결제 실패 처리 + SagaState | `HandlePaymentFailureService` | ✅ fail + compensation |
| Order: 결제 성공 SagaState 완료 | `HandlePaymentSuccessService` | ✅ complete() |
| Order: SagaAdmin API | `SagaAdminController` | ✅ GET/POST 엔드포인트 |
| Order: build.gradle 의존성 | `order/build.gradle` | ✅ event-schema + common-lib |
| Coupon: OrderFailed 헥사고날 Consumer | `OrderFailedKafkaConsumer` | ✅ event-schema 사용 |
| Coupon: DLQ Consumer (기본) | `OrderFailedDLQConsumer` | ✅ 기본 로깅 |
| Coupon: KafkaConfig (Retry + DLQ) | `KafkaConfig` | ✅ ExponentialBackOff + DLQ |
| Payment/Product: event-schema 의존성 | `build.gradle` | ✅ 이미 추가됨 |
| Order: OutboxEventHelper | `OutboxEventHelper` | ✅ 트랜잭션 컨텍스트 참여 |

### 1-2. 남은 구현 항목 ❌

| # | 항목 | 서비스 | 파일 | 우선순위 |
|---|------|--------|------|----------|
| G-01 | SagaState.start() 미호출 | order | `CreateOrderService` | 🔴 High |
| G-02 | publishInventoryDecrease() 직접 Kafka | order | `KafkaOrderEventPublisher` | 🟡 Medium |
| G-03 | PaymentEventProducer 로컬 DTO 사용 | payment | `PaymentEventProducer` | 🟡 Medium |
| G-04 | OrderFailedKafkaConsumer 로컬 DTO 사용 | payment | `OrderFailedKafkaConsumer`, `PaymentDLQConsumer` | 🟡 Medium |
| G-05 | InventoryEventConsumer 로컬 DTO 사용 | product | `InventoryEventConsumer` | 🔴 High (필드 불일치) |
| G-06 | 레거시 Consumer 중복 활성화 | coupon | `infrastructure/kafka/consumer/` | 🔴 High (중복 소비) |
| G-07 | DLQ Consumer Slack 알림 미구현 | coupon | `OrderFailedDLQConsumer` | 🟠 Low (TODO) |

---

## 2. 아키텍처 설계

### 2-1. Outbox Pattern 전체 플로우

```
[CreateOrderService]
  1. orderRepositoryPort.save(order)            ← DB 트랜잭션 시작
  2. sagaStateRepository.save(SagaState.start)  ← 동일 트랜잭션 내 추가 (신규 G-01)
  3. return CreateOrderResult                   ← DB 커밋

[HandlePaymentSuccessService] (payment-completed 수신)
  1. order.changeStatus(PAID)
  2. orderRepositoryPort.save(order)
  3. outboxEventHelper.saveEvent(               ← Outbox 저장 전환 (신규 G-02)
       "ORDER", orderId, "INVENTORY_DECREASE", "inventory-decrease", event)
  4. [if coupon] publishCouponUsed()            ← 직접 Kafka 유지

[OutboxEventPublisher] (5초 폴링 - common-lib)
  - PENDING → Kafka send → PUBLISHED
  - 실패 시 → retryCount++, 3회 후 FAILED

[HandlePaymentFailureService] (payment-failed 수신) ← 이미 구현됨
  1. order.changeStatus(FAILED)
  2. saga.fail() + saga.startCompensation()
  3. publishInventoryRollback()  → Outbox 저장
  4. publishOrderFailed()        → Outbox 저장
```

### 2-2. event-schema 의존 관계 (목표 상태 To-Be)

```
event-schema 모듈
├── events/inventory/
│   ├── InventoryDecreaseRequestEvent  ← Order(발행) / Product(소비)
│   ├── InventoryDecreasedEvent        ← Product(발행) / Order(소비)
│   ├── InventoryRollbackEvent         ← Order(발행) / Product(소비)
│   └── InventorySoldOutEvent          ← Product(발행) / 전체   [신규 추가]
├── events/order/
│   └── OrderFailedEvent               ← Order(발행) / Payment, Coupon(소비)
└── events/payment/
    ├── PaymentCompletedEvent          ← Payment(발행) / Order(소비)
    └── PaymentFailedEvent             ← Payment(발행) / Order(소비)

삭제 대상 로컬 DTO:
  payment/infrastructure/kafka/event/ → 3개 삭제
  product/product/infrastructure/kafka/event/ → 3개 삭제
```

### 2-3. SagaState 라이프사이클

```
주문 생성         → SagaState.start("ORDER_CREATION", orderId)  [신규 G-01]
                   status = STARTED, currentStep = "INIT"

결제 성공 수신    → saga.updateStep("INVENTORY_DECREASING")  [선택]
                   status = RUNNING

보상 트랜잭션     → saga.fail(reason) → saga.startCompensation()  [이미 구현]
                   status = COMPENSATING

완료              → saga.complete()  [이미 구현]
                   status = COMPLETED

보상 완료 (수동)  → saga.completeCompensation()  [SagaAdminController]
                   status = COMPENSATED
```

---

## 3. 컴포넌트별 설계

### 3-1. Order 서비스

#### G-01: CreateOrderService — SagaState.start() 추가

**파일**: `order/application/service/CreateOrderService.java`

**변경 내용**:
- `SagaStateRepository` 주입 추가
- `ObjectMapper` 주입 추가 (페이로드 직렬화)
- `orderRepositoryPort.save()` 직후, `SagaState.start()` 저장 (동일 트랜잭션)
- try-catch 감싸기 (SagaState 실패가 메인 플로우에 영향 없도록)

```java
// 추가 주입
private final SagaStateRepository sagaStateRepository;
private final ObjectMapper objectMapper;

// 저장 후 추가 로직
Order savedOrder = orderRepositoryPort.save(order);

try {
    String payload = objectMapper.writeValueAsString(Map.of(
        "productId", command.productId().toString(),
        "quantity", command.orderQuantity(),
        "userId", command.userId().toString()
    ));
    SagaState saga = SagaState.start("ORDER_CREATION", savedOrder.getId(), payload);
    sagaStateRepository.save(saga);
    log.info("[CreateOrderService] SagaState 생성 완료 - orderId: {}", savedOrder.getId());
} catch (Exception e) {
    log.warn("[Saga] 상태 생성 실패 (무시) - orderId: {}, error: {}", savedOrder.getId(), e.getMessage());
}
```

#### G-02: KafkaOrderEventPublisher — publishInventoryDecrease Outbox 전환

**파일**: `order/adapter/out/messaging/KafkaOrderEventPublisher.java`

```java
// Before: 직접 Kafka
@Override
public void publishInventoryDecrease(UUID orderId, UUID productId, int quantity) {
    InventoryDecreaseRequestEvent event = InventoryDecreaseRequestEvent.of(orderId, productId, quantity);
    kafkaTemplate.send("inventory-decrease", orderId.toString(), event);
}

// After: Outbox 저장
@Override
public void publishInventoryDecrease(UUID orderId, UUID productId, int quantity) {
    InventoryDecreaseRequestEvent event = InventoryDecreaseRequestEvent.of(orderId, productId, quantity);
    outboxEventHelper.saveEvent("ORDER", orderId, "INVENTORY_DECREASE", "inventory-decrease", event);
    log.info("[KafkaOrderEventPublisher] 재고 감소 이벤트 Outbox 저장 - orderId: {}", orderId);
}
```

**KafkaInventoryEventPublisher도 동일하게 수정**:
- `decreaseInventory()` → Outbox 전환
- `rollbackInventory()` → Outbox 전환 (현재 직접 Kafka)
- `OutboxEventHelper` 주입 추가

---

### 3-2. Product 서비스

#### G-05: InventoryEventConsumer — event-schema 교체 (필드 불일치 주의)

**파일**: `product/product/infrastructure/kafka/consumer/InventoryEventConsumer.java`

**필드 변경 매핑**:

| 항목 | 변경 전 | 변경 후 |
|------|---------|---------|
| import | `product.product.infrastructure.kafka.event.OrderRequestedInventoryEvent` | `events.inventory.InventoryDecreaseRequestEvent` |
| import | `product.product.infrastructure.kafka.event.InventoryDecreasedEvent` | `events.inventory.InventoryDecreasedEvent` |
| import | `product.product.infrastructure.kafka.event.InventoryRollbackEvent` | `events.inventory.InventoryRollbackEvent` |
| 필드 접근 | `event.decreasedQuantity()` | `event.quantity()` |
| KafkaListener 파라미터 타입 | `OrderRequestedInventoryEvent` | `InventoryDecreaseRequestEvent` |

**파일**: `product/product/infrastructure/kafka/consumer/InventoryTestProducerController.java`

- 테스트용 컨트롤러 — 로컬 DTO 사용 유지 또는 event-schema로 교체 (선택)

**InventoryService.java**:
- `InventorySoldOutEvent` — event-schema 신규 추가 후 import 교체

**삭제 대상 로컬 DTO**:
- `product/product/infrastructure/kafka/event/InventoryDecreasedEvent.java`
- `product/product/infrastructure/kafka/event/InventoryRollbackEvent.java`
- `product/product/infrastructure/kafka/event/OrderRequestedInventoryEvent.java`

#### event-schema 신규 추가: InventorySoldOutEvent

**파일**: `event-schema/src/main/java/com/live_commerce/events/inventory/InventorySoldOutEvent.java`

```java
package com.live_commerce.events.inventory;

import java.util.UUID;

/**
 * 재고 소진 이벤트 (Product → 전체)
 * Producer: product-service
 * Consumer: 관련 서비스 (재고 소진 알림)
 * Topic: inventory-sold-out
 */
public record InventorySoldOutEvent(
    UUID productId,
    UUID orderId
) {
    public static InventorySoldOutEvent of(UUID productId, UUID orderId) {
        return new InventorySoldOutEvent(productId, orderId);
    }
}
```

---

### 3-3. Payment 서비스

#### G-03, G-04: 로컬 event DTO → event-schema 교체

**필드 호환성** (동일 필드, import만 변경):

| 로컬 클래스 | event-schema 클래스 | 필드 동일 여부 |
|-------------|---------------------|----------------|
| `payment.infrastructure.kafka.event.PaymentCompletedEvent` | `events.payment.PaymentCompletedEvent` | ✅ 동일 |
| `payment.infrastructure.kafka.event.PaymentFailedEvent` | `events.payment.PaymentFailedEvent` | 확인 필요 |
| `payment.infrastructure.kafka.event.OrderFailedEvent` | `events.order.OrderFailedEvent` | ✅ 동일 |

**변경 파일**:
- `PaymentEventProducer.java` — import 교체 2개
- `OrderFailedKafkaConsumer.java` — import 교체 1개
- `PaymentDLQConsumer.java` — import 교체 1개

**삭제 대상**:
- `payment/infrastructure/kafka/event/PaymentCompletedEvent.java`
- `payment/infrastructure/kafka/event/PaymentFailedEvent.java`
- `payment/infrastructure/kafka/event/OrderFailedEvent.java`

---

### 3-4. Coupon 서비스

#### G-06: 레거시 Consumer 중복 소비 방지 (중요)

**문제**: 동일 토픽을 구독하는 Consumer가 2개씩 존재:
- `infrastructure/kafka/consumer/OrderFailedEventConsumer` (레거시)
- `adapter/in/kafka/OrderFailedKafkaConsumer` (헥사고날 신규)

두 Consumer가 서로 다른 `groupId`로 구독 시 → 메시지가 두 번 처리됨!

**groupId 확인**:
- `OrderFailedEventConsumer` groupId: 확인 필요 (소비 중단 대상)
- `OrderFailedKafkaConsumer` groupId: `${spring.application.name}-hexagonal`

**처리 방법**: 레거시 3개 Consumer에서 `@Component` 어노테이션 제거

**대상 파일**:
- `coupon/infrastructure/kafka/consumer/OrderFailedEventConsumer.java` — `@Component` 제거
- `coupon/infrastructure/kafka/consumer/CouponUsedEventConsumer.java` — `@Component` 제거
- `coupon/infrastructure/kafka/consumer/FirstJoinCouponEventConsumer.java` — `@Component` 제거

#### G-07: OrderFailedDLQConsumer Slack 알림 (현 단계 보류)

**결정**: 현 단계에서는 `log.error()` 기반 모니터링 유지. Notification Feign 연동은 별도 PDCA 대상.

---

## 4. ADR (Architecture Decision Records)

### ADR-1: Outbox 적용 범위 — 보상/신뢰성 이벤트 우선

| 이벤트 | 방식 | 이유 |
|--------|------|------|
| `inventory-rollback` | Outbox ✅ (기존) | 보상 이벤트 - 유실 시 재고 불일치 |
| `order-failed` | Outbox ✅ (기존) | 보상 이벤트 - 환불/쿠폰복구 트리거 |
| `inventory-decrease` | Outbox (변경) | 결제 후 재고 차감 실패 방지 |
| `coupon-used` | Direct Kafka (유지) | 멱등성 보장, 실패 시 결제 유지됨 |
| `payment-completed` | Direct Kafka (유지) | Payment 서비스 자체 책임 |
| `payment-failed` | Direct Kafka (유지) | Payment 서비스 자체 책임 |

### ADR-2: SagaState — Best Effort (실패 허용)

SagaState 저장 실패는 try-catch로 warn 로깅만 하고 메인 트랜잭션을 중단하지 않음.
SagaState는 추적/모니터링 목적이며, 없어도 실제 보상 트랜잭션은 동작함.

### ADR-3: 로컬 event DTO 마이그레이션 전략

기존 로컬 DTO를 즉시 삭제하고 event-schema로 직접 교체. 단계적 deprecation 없음.
필드 불일치가 있는 product 서비스는 코드 수정 후 빌드 검증 필수.

### ADR-4: 레거시 Consumer 비활성화 방법

`@Component` 어노테이션 제거로 Spring Bean 등록 차단.
파일 자체는 유지 (히스토리 보존). 추후 별도 커밋으로 파일 삭제.

### ADR-5: InventorySoldOutEvent event-schema 추가

product 서비스 내부 이벤트이지만 다른 서비스가 구독할 수 있으므로 event-schema에 추가.
현재 구독 서비스가 없더라도 통일성을 위해 event-schema에서 관리.

---

## 5. 구현 순서 (Implementation Guide)

```
Step 1: event-schema 보완 (15분)
  └── InventorySoldOutEvent.java 신규 생성

Step 2: Product 서비스 event-schema 교체 (1~2시간)
  ├── InventoryEventConsumer.java — import + 필드명 수정
  ├── InventoryService.java — InventorySoldOutEvent import 교체
  ├── 로컬 DTO 3개 삭제
  └── ./gradlew :product:compileJava

Step 3: Payment 서비스 event-schema 교체 (30분)
  ├── PaymentEventProducer.java — import 교체
  ├── OrderFailedKafkaConsumer.java — import 교체
  ├── PaymentDLQConsumer.java — import 교체
  ├── 로컬 DTO 3개 삭제
  └── ./gradlew :payment:compileJava

Step 4: Order 서비스 — SagaState 시작점 추가 (30분)
  ├── CreateOrderService.java — SagaStateRepository + ObjectMapper 주입
  ├── SagaState.start() 호출 추가 (try-catch)
  └── ./gradlew :order:compileJava

Step 5: Order 서비스 — publishInventoryDecrease Outbox 전환 (30분)
  ├── KafkaOrderEventPublisher.java — publishInventoryDecrease Outbox 전환
  ├── KafkaInventoryEventPublisher.java — Outbox 전환 + OutboxEventHelper 주입
  └── ./gradlew :order:compileJava

Step 6: Coupon 레거시 Consumer 비활성화 (15분)
  ├── OrderFailedEventConsumer.java — @Component 제거
  ├── CouponUsedEventConsumer.java — @Component 제거
  ├── FirstJoinCouponEventConsumer.java — @Component 제거
  └── ./gradlew :coupon:compileJava

Step 7: 전체 빌드 및 테스트 (1시간)
  └── ./gradlew build test
```

---

## 6. DB 스키마 (common-lib Entity 기반)

`OutboxEvent`와 `SagaState`는 `common-lib`의 `@Entity`로 JPA `ddl-auto`에 따라 자동 생성.

**테이블 위치 주의**: `@Table`에 스키마 속성 없으므로 Order 서비스 JPA 기본 스키마에 생성됨.
운영 환경에서 `orders` 스키마로 격리하려면 `@Table(name="...", schema="orders")` 확인 필요.

**H2 테스트 환경**: `ddl-auto: create`로 자동 생성됨 (별도 SQL 불필요).

---

## 7. 성공 기준 (Acceptance Criteria)

| # | 기준 | 검증 방법 |
|---|------|-----------|
| AC-01 | 주문 생성 시 SagaState 레코드 생성됨 (status=STARTED) | `/api/v1/admin/saga/{orderId}` |
| AC-02 | payment-completed 수신 후 inventory-decrease가 outbox_event에 저장됨 | DB 조회 |
| AC-03 | payment-failed 수신 후 SagaState가 COMPENSATING으로 전환됨 | `/api/v1/admin/saga/{orderId}` |
| AC-04 | payment-failed 수신 후 inventory-rollback, order-failed가 outbox_event에 저장됨 | DB 조회 |
| AC-05 | product 서비스가 event-schema InventoryRollbackEvent 역직렬화 성공 | 로그 확인 |
| AC-06 | payment 서비스가 event-schema PaymentCompletedEvent 발행 성공 | 로그 확인 |
| AC-07 | coupon 레거시 Consumer 비활성화 후 중복 소비 없음 | 로그 확인 |
| AC-08 | 쿠폰 복구 3회 재시도 후 order-failed.DLQ에 메시지 도달 | DLQ Consumer 로그 |
| AC-09 | `./gradlew build` 성공 | CI 빌드 |
| AC-10 | `./gradlew test` 기존 테스트 전부 통과 | 테스트 결과 |

---

*Design 문서 완료. 다음 단계: `/pdca do compensation-transaction`*
