# Design: 보상 트랜잭션 (Compensation Transaction) 안정화

**Feature:** compensation-transaction
**작성일:** 2026-02-13
**참조 Plan:** `docs/01-plan/features/compensation-transaction.plan.md`
**PDCA 단계:** Design

---

## 1. 시스템 아키텍처 개요

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Live Commerce MSA                               │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  Order Service (변경 핵심)                                        │   │
│  │  ┌─────────────┐  ①원자적 저장  ┌────────────────────────────┐  │   │
│  │  │OrderCreate  │──────────────→│ outbox_events (DB Table)   │  │   │
│  │  │Service      │               │ saga_states   (DB Table)   │  │   │
│  │  └─────────────┘               └────────────────────────────┘  │   │
│  │                                          ↓ ②5초 폴링            │   │
│  │  ┌──────────────────────────────────────────────────────────┐  │   │
│  │  │  OutboxEventPublisher (@Scheduled)                       │  │   │
│  │  │  └→ Kafka 발행 → PUBLISHED 상태 업데이트                  │  │   │
│  │  └──────────────────────────────────────────────────────────┘  │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                              ③ Kafka Events                             │
│  ┌──────────────┐   inventory-rollback  ┌───────────────────────────┐  │
│  │Product Svc   │←──────────────────────│                           │  │
│  │(재고 복구)    │   order-failed         │  Kafka Topics             │  │
│  └──────────────┘←──────────────────────│                           │  │
│  ┌──────────────┐   order-failed         │                           │  │
│  │Payment Svc   │←──────────────────────│                           │  │
│  │(환불)        │                        └───────────────────────────┘  │
│  ┌──────────────┐   order-failed                                        │
│  │Coupon Svc    │←──────────────────────                               │
│  │(쿠폰 복구)   │                                                        │
│  └──────────────┘                                                       │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Phase 1: event-schema 모듈 통합

### 2-1. 현재 문제 & 해결 방향

**문제:** `settings.gradle`에 `common-lib`, `event-schema`가 포함되지 않음 → Gradle 멀티 프로젝트 빌드 불가

```groovy
// 현재 settings.gradle (누락)
include ':order'
include ':payment'
// ...

// 변경 후
include ':event-schema'  // 추가
include ':common-lib'    // 추가
include ':order'
include ':payment'
// ...
```

### 2-2. event-schema 모듈 구조 (이미 구현 완료, 통합만 필요)

```
event-schema/src/main/java/com/live_commerce/events/
├── payment/
│   ├── PaymentCompletedEvent.java  ← record(orderId, paymentId, amount, completedAt)
│   └── PaymentFailedEvent.java     ← record(orderId, reason, failedAt)
├── inventory/
│   ├── InventoryDecreasedEvent.java ← record(orderId, productId, quantity, decreasedAt)
│   └── InventoryRollbackEvent.java  ← record(orderId, productId, quantity, reason, rollbackAt)
└── order/
    └── OrderFailedEvent.java        ← record(orderId, message)
```

### 2-3. 서비스별 build.gradle 변경

각 서비스의 `build.gradle` dependencies에 추가:

```groovy
// order, payment, product, coupon 서비스 공통
implementation project(':event-schema')
```

### 2-4. 교체 대상 파일 매핑

| 서비스 | 삭제할 기존 파일 | event-schema 대체 클래스 |
|--------|-----------------|--------------------------|
| order | `kafkaOrder/payment/PaymentCompletedEvent.java` | `com.live_commerce.events.payment.PaymentCompletedEvent` |
| order | `kafkaOrder/payment/PaymentFailedEvent.java` | `com.live_commerce.events.payment.PaymentFailedEvent` |
| order | `kafkaOrder/product/InventoryRollbackEvent.java` | `com.live_commerce.events.inventory.InventoryRollbackEvent` |
| order | `kafkaOrder/product/OrderRequestedInventoryEvent.java` | `com.live_commerce.events.inventory.InventoryDecreasedEvent` 참고 |
| order | `PaymentFailureServiceKafka.OrderFailedEvent` (inner record) | `com.live_commerce.events.order.OrderFailedEvent` |
| payment | 서비스 내 이벤트 DTO | event-schema 교체 |
| product | 서비스 내 이벤트 DTO | event-schema 교체 |
| coupon | 서비스 내 이벤트 DTO | event-schema 교체 |

> **주의:** `OrderRequestedInventoryEvent`는 event-schema에 없으므로 추가 필요하거나 기존 유지.

---

## 3. Phase 2: Outbox Pattern 설계

### 3-1. common-lib build.gradle 수정

현재 common-lib은 JPA와 Kafka 의존성이 없어 Outbox 구현 불완전. 추가 필요:

```groovy
// common-lib/build.gradle - 추가할 의존성
dependencies {
    // 기존 의존성 유지하되 불필요한 web/security 제거 후:

    // JPA (OutboxEvent @Entity용)
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

    // Kafka (OutboxEventPublisher KafkaTemplate용)
    implementation 'org.springframework.kafka:spring-kafka'

    // Jackson (JSON 직렬화)
    implementation 'com.fasterxml.jackson.core:jackson-databind'
    implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'

    // Lombok
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
}
```

### 3-2. OutboxEvent 스키마 (orders 스키마)

```sql
-- orders 스키마에 생성
CREATE TABLE outbox_event (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(50)  NOT NULL,      -- 'ORDER'
    aggregate_id    UUID         NOT NULL,       -- orderId
    event_type      VARCHAR(100) NOT NULL,       -- 'INVENTORY_ROLLBACK', 'ORDER_FAILED'
    topic           VARCHAR(100) NOT NULL,       -- Kafka topic 이름
    payload         TEXT         NOT NULL,       -- JSON 직렬화 이벤트
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING, PUBLISHED, FAILED
    retry_count     INT          NOT NULL DEFAULT 0,
    error_message   TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP
);

CREATE INDEX idx_outbox_status ON outbox_event(status);
CREATE INDEX idx_outbox_created_at ON outbox_event(created_at);
```

### 3-3. OutboxEvent 엔티티 (현재 스켈레톤 완성도 95%)

현재 `OutboxEvent.java`의 상태 enum명 불일치 수정 필요:

```java
// OutboxStatus.java - 현재
public enum OutboxStatus {
    PENDING, PUBLISHED, FAILED   // "PUBLISHED" 사용

// OutboxEvent.java에서 "SENT"로 호출 → "PUBLISHED"로 통일 필요
public void markPublished() {         // ← 이미 PUBLISHED로 정렬
    this.status = OutboxStatus.PUBLISHED;
```

**현재 OutboxEvent 스켈레톤 분석:**
- `canRetry()`: `retryCount < 5` 체크 → Plan에서 정의한 3회와 불일치. **3으로 변경 필요**
- `resetForRetry()`: `retryCount < 5` → **3으로 변경 필요**
- 나머지: 그대로 사용 가능

### 3-4. OutboxEventPublisher 수정 사항

현재 스켈레톤의 문제점:

```java
// 문제: kafkaTemplate<String, String> - payload가 String이므로 OK
// 하지만 비동기 콜백 내에서 outboxEventRepository.save()는
// 트랜잭션 컨텍스트가 다를 수 있음

// 개선: whenComplete 콜백에서 별도 @Transactional 메서드 호출
```

개선된 설계:
```java
// OutboxEventPublisher
@Scheduled(fixedDelay = 5000)
public void publishPendingEvents() {
    List<OutboxEvent> events = outboxEventRepository.findByStatusOrderByCreatedAtAsc(PENDING);
    for (OutboxEvent event : events) {
        publishEvent(event);  // 동기 방식으로 변경
    }
}

// 동기 발행 (send().get()으로 블로킹 확인)
private void publishEvent(OutboxEvent event) {
    try {
        kafkaTemplate.send(event.getTopic(), event.getAggregateId().toString(), event.getPayload())
            .get(5, TimeUnit.SECONDS);  // 최대 5초 대기
        updateStatus(event.getId(), true, null);
    } catch (Exception e) {
        updateStatus(event.getId(), false, e.getMessage());
    }
}

@Transactional
public void updateStatus(UUID eventId, boolean success, String error) {
    OutboxEvent event = outboxEventRepository.findById(eventId).orElseThrow();
    if (success) event.markPublished();
    else event.markFailed(error);
    outboxEventRepository.save(event);
}
```

### 3-5. Order 서비스에서의 Outbox 사용 패턴

#### 핵심 원칙: DB 트랜잭션 내에서 이벤트 저장 (원자성 보장)

```java
// PaymentFailureServiceKafka.java - 변경 전
@Transactional
public void handlePaymentFailure(PaymentFailedEvent event) {
    order.changeStatus(FAILED);

    inventoryEventProducer.sendInventoryRollbackEvent(rollbackEvent);  // ← 직접 Kafka 발행
    kafkaTemplate.send("order-failed", orderId.toString(), orderFailedEvent);  // ← 직접 Kafka 발행
}

// PaymentFailureServiceKafka.java - 변경 후
@Transactional
public void handlePaymentFailure(PaymentFailedEvent event) {
    order.changeStatus(FAILED);
    sagaState.startCompensation();  // SagaState 업데이트

    // Outbox에 저장 (동일 트랜잭션)
    outboxEventRepository.save(
        OutboxEvent.create("ORDER", orderId, "INVENTORY_ROLLBACK",
                           "inventory-rollback", toJson(rollbackEvent))
    );
    outboxEventRepository.save(
        OutboxEvent.create("ORDER", orderId, "ORDER_FAILED",
                           "order-failed", toJson(orderFailedEvent))
    );
    // Kafka 직접 발행 제거 → OutboxEventPublisher가 비동기 처리
}
```

### 3-6. JSON 직렬화 유틸리티

Order 서비스에 추가할 헬퍼:
```java
// OutboxEventHelper.java (order 서비스 infrastructure 패키지)
@Component
@RequiredArgsConstructor
public class OutboxEventHelper {
    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional  // 호출자의 트랜잭션에 참여
    public void saveEvent(String aggregateType, UUID aggregateId,
                          String eventType, String topic, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxEventRepository.save(
                OutboxEvent.create(aggregateType, aggregateId, eventType, topic, json)
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Outbox 이벤트 직렬화 실패", e);
        }
    }
}
```

---

## 4. Phase 3: Saga State 추적 설계

### 4-1. SagaState 스키마 (orders 스키마)

```sql
CREATE TABLE saga_state (
    saga_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_type       VARCHAR(50)  NOT NULL,       -- 'ORDER_CREATION'
    aggregate_id    UUID         NOT NULL UNIQUE, -- orderId
    status          VARCHAR(20)  NOT NULL,        -- STARTED, RUNNING, COMPLETED, FAILED, COMPENSATING, COMPENSATED
    current_step    VARCHAR(50)  NOT NULL,        -- 'INIT', 'INVENTORY_DECREASING', ...
    payload         TEXT,                         -- 추가 컨텍스트 JSON
    error_message   TEXT,
    retry_count     INT          NOT NULL DEFAULT 0,
    started_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMP
);

CREATE INDEX idx_saga_aggregate_id ON saga_state(aggregate_id);
CREATE INDEX idx_saga_status ON saga_state(status);
```

### 4-2. Saga Step 정의

```
ORDER_CREATION Saga Steps:
┌──────────┐
│  INIT    │ ← Order 저장 완료
└──────────┘
     ↓
┌────────────────────────┐
│  INVENTORY_DECREASING  │ ← inventory-decrease 이벤트 발행
└────────────────────────┘
     ↓
┌────────────────────────┐
│  INVENTORY_DECREASED   │ ← inventory-decreased 수신
└────────────────────────┘
     ↓
┌──────────────────┐
│  PAYMENT_READY   │ ← 결제 준비 요청
└──────────────────┘
     ↓
┌──────────────────┐
│  PAYMENT_DONE    │ ← payment-completed 수신 → COMPLETED
└──────────────────┘

실패 경로:
┌──────────────┐  → COMPENSATING
│  FAILED      │     ↓
└──────────────┘  INVENTORY_ROLLING_BACK
                     ↓
                  PAYMENT_REFUNDING
                     ↓
                  COMPENSATED
```

### 4-3. Order 서비스 SagaState 통합 위치

| 위치 | 단계 변경 |
|------|----------|
| `OrderCreateServiceKafka.orderCreator()` - 주문 저장 직후 | `SagaState.start("ORDER_CREATION", orderId, ...)` + step="INIT" |
| `PaymentStatusTransitionServiceKafka` - 재고 감소 요청 시 | `sagaState.updateStep("INVENTORY_DECREASING")` |
| `PaymentStatusTransitionServiceKafka` - inventory-decreased 수신 | `sagaState.updateStep("INVENTORY_DECREASED")` |
| `PaymentStatusTransitionServiceKafka` - 결제 준비 | `sagaState.updateStep("PAYMENT_READY")` |
| `PaymentSuccessServiceKafka` - payment-completed 수신 | `sagaState.complete()` |
| `PaymentFailureServiceKafka` - payment-failed 처리 | `sagaState.fail(message)` → `sagaState.startCompensation()` |
| 보상 완료 확인 로직 | `sagaState.completeCompensation()` |

### 4-4. SagaState 조회 API (관리자용)

```
GET /api/v1/admin/saga/{orderId}
Response:
{
  "sagaId": "uuid",
  "orderId": "uuid",
  "status": "COMPENSATED",
  "currentStep": "PAYMENT_REFUNDING",
  "errorMessage": "KakaoPay API 오류",
  "startedAt": "2026-02-13T10:00:00",
  "completedAt": "2026-02-13T10:00:05"
}
```

---

## 5. Phase 4: Coupon 서비스 DLQ 처리

### 5-1. Coupon 서비스 KafkaConfig 추가

```java
// coupon/infrastructure/config/KafkaConfig.java - 신규 추가
@Bean
public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
    ConsumerFactory<String, Object> consumerFactory,
    KafkaTemplate<String, Object> kafkaTemplate
) {
    factory.setCommonErrorHandler(
        new DefaultErrorHandler(
            (record, ex) -> {
                String dlqTopic = record.topic() + ".DLQ";
                kafkaTemplate.send(dlqTopic, record.key().toString(), record.value());
            },
            new ExponentialBackOff(1000L, 2.0) {{ setMaxElapsedTime(10000L); }}
        )
    );
    factory.getContainerProperties().setAckMode(MANUAL_IMMEDIATE);
    return factory;
}
```

### 5-2. Coupon DLQ Consumer 신규 추가

```java
// coupon/infrastructure/kafka/consumer/OrderFailedDLQConsumer.java
@KafkaListener(topics = "order-failed.DLQ", groupId = "${spring.application.name}-dlq")
public void onOrderFailedDLQ(OrderFailedEvent event, Acknowledgment ack) {
    log.error("[DLQ] 쿠폰 복구 최종 실패 - orderId: {}", event.orderId());
    // Slack 알림 (NotificationService Feign 또는 직접 Webhook)
    ack.acknowledge();
}
```

---

## 6. 시퀀스 다이어그램

### 6-1. 정상 플로우 (Outbox 적용 후)

```
Client → OrderController → OrderCreateServiceKafka
                               ↓
                   [DB Transaction 시작]
                   orderRepository.save(order)         → orders.order
                   sagaStateRepository.save(saga)      → orders.saga_state
                   outboxEventRepository.save(event)   → orders.outbox_event
                   [DB Transaction 커밋]
                               ↓
                   (5초 후) OutboxEventPublisher 폴링
                               ↓
                   KafkaTemplate.send("inventory-decrease")
                   outboxEvent.markPublished()
                               ↓
                   ProductService: 재고 감소
                   → Kafka: inventory-decreased
                               ↓
                   OrderService: payment 준비 시작
                   → KakaoPay API
                   → Kafka: payment-completed
                               ↓
                   OrderService: 주문 완료
                   SagaState.complete()
```

### 6-2. 보상 트랜잭션 플로우 (Outbox + SagaState 적용 후)

```
PaymentService: 결제 실패
→ Kafka: payment-failed

OrderService: PaymentEventConsumer.listenPaymentFailed()
    → PaymentFailureServiceKafka.handlePaymentFailure()

    [DB Transaction 시작]
    order.changeStatus(FAILED)
    sagaState.fail("결제 실패: " + message)
    sagaState.startCompensation()

    outboxEvent.save("INVENTORY_ROLLBACK", "inventory-rollback", payload)
    outboxEvent.save("ORDER_FAILED", "order-failed", payload)
    [DB Transaction 커밋]  ← 원자적 보장

    ↓ (5초 후 OutboxEventPublisher)

    Kafka: inventory-rollback → ProductService 재고 복구
    Kafka: order-failed     → PaymentService 환불
                            → CouponService 쿠폰 복구

    (보상 완료 확인은 별도 이벤트 or 타임아웃 기반)
    sagaState.completeCompensation()
```

---

## 7. 클래스 다이어그램 (Order 서비스 변경 핵심)

```
OrderCreateServiceKafka
  ├── OrderRepository
  ├── SagaStateRepository  ← [신규 의존]
  └── OutboxEventHelper    ← [신규 의존]

PaymentFailureServiceKafka
  ├── OrderRepository
  ├── SagaStateRepository  ← [신규 의존]
  ├── OutboxEventHelper    ← [신규 의존]
  └── ~~InventoryEventProducer~~ ← [제거]
  └── ~~KafkaTemplate~~ ← [제거]

OutboxEventHelper (신규)
  ├── ObjectMapper
  └── OutboxEventRepository

OutboxEventPublisher (common-lib - 현재 스켈레톤 수정)
  ├── OutboxEventRepository
  └── KafkaTemplate<String, String>

SagaAdminController (신규)
  └── SagaStateRepository
```

---

## 8. 변경 파일 목록

### Phase 1 (event-schema 통합)

| 파일 | 변경 유형 | 내용 |
|------|----------|------|
| `settings.gradle` | 수정 | `include ':event-schema'`, `':common-lib'` 추가 |
| `order/build.gradle` | 수정 | `implementation project(':event-schema')` 추가 |
| `payment/build.gradle` | 수정 | `implementation project(':event-schema')` 추가 |
| `product/build.gradle` | 수정 | `implementation project(':event-schema')` 추가 |
| `coupon/build.gradle` | 수정 | `implementation project(':event-schema')` 추가 |
| `order/kafkaOrder/payment/PaymentCompletedEvent.java` | 삭제 | event-schema로 교체 |
| `order/kafkaOrder/payment/PaymentFailedEvent.java` | 삭제 | event-schema로 교체 |
| `order/kafkaOrder/product/InventoryRollbackEvent.java` | 삭제 | event-schema로 교체 |
| `PaymentFailureServiceKafka.OrderFailedEvent` | 삭제 | event-schema로 교체 |
| 각 서비스 import문 | 수정 | event-schema 패키지로 변경 |

### Phase 2 (Outbox Pattern)

| 파일 | 변경 유형 | 내용 |
|------|----------|------|
| `common-lib/build.gradle` | 수정 | JPA, Kafka 의존성 추가 |
| `common-lib/outbox/OutboxEvent.java` | 수정 | retryCount 임계값 5→3 변경 |
| `common-lib/outbox/OutboxEventPublisher.java` | 수정 | 동기 발행 방식으로 변경, updateStatus 분리 |
| `order/build.gradle` | 수정 | `implementation project(':common-lib')` 추가 |
| `order/infrastructure/outbox/OutboxEventHelper.java` | 신규 | JSON 직렬화 + 저장 헬퍼 |
| `order/PaymentFailureServiceKafka.java` | 수정 | Kafka 직접 발행 → Outbox 저장으로 변경 |
| `order/PaymentSuccessServiceKafka.java` | 수정 | Kafka 직접 발행 → Outbox 저장으로 변경 |

### Phase 3 (Saga State)

| 파일 | 변경 유형 | 내용 |
|------|----------|------|
| `common-lib/saga/SagaState.java` | 확인 | 스켈레톤 완성도 확인 (현재 구현 완료) |
| `order/OrderCreateServiceKafka.java` | 수정 | SagaState 생성 로직 추가 |
| `order/PaymentFailureServiceKafka.java` | 수정 | SagaState fail/startCompensation 추가 |
| `order/PaymentSuccessServiceKafka.java` | 수정 | SagaState complete 추가 |
| `order/PaymentStatusTransitionServiceKafka.java` | 수정 | 각 단계별 updateStep 추가 |
| `order/presentation/SagaAdminController.java` | 신규 | Saga 상태 조회 API |

### Phase 4 (Coupon DLQ)

| 파일 | 변경 유형 | 내용 |
|------|----------|------|
| `coupon/infrastructure/config/KafkaConfig.java` | 신규 | Retry + DLQ 설정 |
| `coupon/infrastructure/kafka/consumer/OrderFailedDLQConsumer.java` | 신규 | DLQ Consumer |

---

## 9. 테스트 설계

### 단위 테스트

| 테스트 대상 | 시나리오 | 기대 결과 |
|------------|----------|-----------|
| `PaymentFailureServiceKafka` | 결제 실패 처리 | OutboxEvent 2건 저장, SagaState COMPENSATING |
| `PaymentFailureServiceKafka` | 이미 FAILED인 주문 | 멱등성: early return |
| `OutboxEventPublisher` | PENDING 이벤트 발행 | Kafka 발행 + PUBLISHED 상태 |
| `OutboxEventPublisher` | Kafka 발행 실패 | FAILED 상태 + retryCount 증가 |
| `OutboxEventPublisher` | retryCount=3 초과 | FAILED 상태 유지 (재시도 안함) |
| `SagaState` | 정상 완료 플로우 | STARTED → RUNNING → COMPLETED |
| `SagaState` | 보상 완료 플로우 | FAILED → COMPENSATING → COMPENSATED |

### 통합 테스트 시나리오

```
1. 결제 실패 시나리오:
   - 주문 생성 → payment-failed 이벤트 시뮬레이션
   - 기대: OutboxEvent 2건 PENDING 상태로 저장
   - 기대: SagaState = COMPENSATING
   - Outbox 폴링 후: inventory-rollback, order-failed 발행
   - 기대: SagaState = COMPENSATED

2. 중복 이벤트 처리:
   - 동일 payment-failed 이벤트 2번 수신
   - 기대: 두 번째는 early return (멱등성)

3. Kafka 브로커 장애 시:
   - DB 커밋 성공, Kafka 다운
   - 기대: OutboxEvent PENDING 유지
   - Kafka 복구 후: 자동 발행 재시도
```

---

## 10. 데이터 마이그레이션

### Flyway 마이그레이션 스크립트

```sql
-- order 서비스: src/main/resources/db/migration/V2__add_outbox_saga.sql

-- Outbox Events 테이블
CREATE TABLE IF NOT EXISTS outbox_event (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    UUID         NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    topic           VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count     INT          NOT NULL DEFAULT 0,
    error_message   TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP
);
CREATE INDEX idx_outbox_status ON outbox_event(status);
CREATE INDEX idx_outbox_created_at ON outbox_event(created_at);

-- Saga State 테이블
CREATE TABLE IF NOT EXISTS saga_state (
    saga_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_type       VARCHAR(50)  NOT NULL,
    aggregate_id    UUID         NOT NULL UNIQUE,
    status          VARCHAR(20)  NOT NULL,
    current_step    VARCHAR(50)  NOT NULL,
    payload         TEXT,
    error_message   TEXT,
    retry_count     INT          NOT NULL DEFAULT 0,
    started_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMP
);
CREATE INDEX idx_saga_aggregate_id ON saga_state(aggregate_id);
CREATE INDEX idx_saga_status ON saga_state(status);
```

> **주의:** 현재 order 서비스에 Flyway 설정이 없는 경우 `spring.jpa.hibernate.ddl-auto=update`로 대체 가능 (개발환경)

---

## 11. 성능 고려사항

| 항목 | 현재 | 개선 후 | 비고 |
|------|------|---------|------|
| 이벤트 발행 지연 | 즉시 (Kafka 직접) | 최대 5초 (Outbox 폴링) | 허용 범위 |
| DB 부하 | +0 | +폴링 쿼리 5초마다 | 인덱스로 최적화 |
| 중복 발행 | 없음 | 가능 (At-Least-Once) | Consumer 멱등성으로 처리 |

---

*Design 문서 완료. 다음 단계: `/pdca do compensation-transaction`*
