# compensation-transaction Analysis Report

> **Analysis Type**: Gap Analysis (Design vs Implementation)
>
> **Project**: Live Commerce Platform
> **Analyst**: gap-detector (Claude Code)
> **Date**: 2026-02-13
> **Design Doc**: [compensation-transaction.design.md](../02-design/features/compensation-transaction.design.md)

---

## 1. Analysis Overview

### 1.1 Analysis Purpose

Design 문서(compensation-transaction.design.md)와 실제 구현 코드 간의 Gap을 분석하여 구현 완성도를 검증한다. Outbox Pattern, Saga State, event-schema 통합, Coupon DLQ 등 보상 트랜잭션 안정화 전체 범위를 대상으로 한다.

### 1.2 Analysis Scope

- **Design Document**: `docs/02-design/features/compensation-transaction.design.md`
- **Implementation Path**: `event-schema/`, `common-lib/`, `order/`, `coupon/`
- **Analysis Date**: 2026-02-13

---

## 2. Phase 1: event-schema 모듈 통합

### 항목 1: settings.gradle에 common-lib, event-schema include

| Design | Implementation | Status |
|--------|---------------|--------|
| `include ':event-schema'` | settings.gradle:3 `include ':event-schema'` | **Match** |
| `include ':common-lib'` | settings.gradle:4 `include ':common-lib'` | **Match** |

**Result**: ✅ 완전 일치

---

### 항목 2: event-schema 이벤트 필드 정렬

| Event | Design 필드 | Implementation 필드 | Status |
|-------|-----------|-------------------|--------|
| PaymentCompletedEvent | `(orderId, paymentId, amount, completedAt)` | `(orderId, message, finalPaidPrice)` | ⚠️ 불일치 |
| PaymentFailedEvent | `(orderId, reason, failedAt)` | `(orderId, message)` | ⚠️ 불일치 |
| OrderFailedEvent | `(orderId, message)` | `(orderId, message)` | ✅ 일치 |
| InventoryRollbackEvent | `(orderId, productId, quantity, reason, rollbackAt)` | `(orderId, productId, quantity, reason, rollbackAt)` | ✅ 일치 |
| InventoryDecreasedEvent | `(orderId, productId, quantity, decreasedAt)` | `(orderId, productId, quantity, decreasedAt)` | ✅ 일치 |

**세부 분석**:

- **PaymentCompletedEvent**: Design에서는 `(orderId, paymentId, amount, completedAt)`를 정의했으나 구현은 `(orderId, message, finalPaidPrice)`로 되어 있다. `paymentId`, `completedAt` 필드가 없고 `message`, `finalPaidPrice` 필드가 추가되었다.
- **PaymentFailedEvent**: Design에서는 `(orderId, reason, failedAt)`를 정의했으나 구현은 `(orderId, message)`로 되어 있다. `failedAt` 필드가 없고 `reason`이 `message`로 명명되었다.

**Result**: ⚠️ 부분 일치 (3/5 이벤트 정확 일치, 2/5 필드 불일치)

---

### 항목 3: InventoryDecreaseRequestEvent 신규 추가

| Design | Implementation | Status |
|--------|---------------|--------|
| Design에 명시: "추가 필요하거나 기존 유지" | `InventoryDecreaseRequestEvent.java` 존재 (orderId, productId, quantity) | ✅ |

**파일 위치**: `/Users/stw/Dev/project/Live-Commerce/event-schema/src/main/java/com/live_commerce/events/inventory/InventoryDecreaseRequestEvent.java`

**Result**: ✅ 구현 완료

---

### 항목 4: 서비스별 build.gradle에 event-schema 의존성

| Service | Design | Implementation | Status |
|---------|--------|---------------|--------|
| order | `implementation project(':event-schema')` | order/build.gradle:87 | ✅ |
| payment | `implementation project(':event-schema')` | payment/build.gradle:97 | ✅ |
| product | `implementation project(':event-schema')` | product/build.gradle:86 | ✅ |
| coupon | `implementation project(':event-schema')` | coupon/build.gradle:72 | ✅ |

**Result**: ✅ 완전 일치 (4/4 서비스)

---

## 3. Phase 2: Outbox Pattern

### 항목 5: common-lib build.gradle에 JPA, Kafka 의존성

| 의존성 | Design | Implementation | Status |
|--------|--------|---------------|--------|
| spring-boot-starter-data-jpa | 필요 | common-lib/build.gradle:32 ✅ | ✅ |
| spring-kafka | 필요 | common-lib/build.gradle:35 ✅ | ✅ |
| jackson-databind | 필요 | common-lib/build.gradle:38 ✅ | ✅ |
| jackson-datatype-jsr310 | 필요 | common-lib/build.gradle:39 ✅ | ✅ |
| lombok | 필요 | common-lib/build.gradle:42-43 ✅ | ✅ |

**추가 구현 항목** (Design에 없음):
- `spring-boot-starter-web` (common-lib/build.gradle:29)
- `spring-boot-starter-validation` (common-lib/build.gradle:30)

**Result**: ✅ 필수 의존성 모두 포함 (추가 의존성 2건 존재)

---

### 항목 6: OutboxEvent retryCount 임계값 3 여부 (5->3 수정)

| 메서드 | Design | Implementation | Status |
|--------|--------|---------------|--------|
| `canRetry()` | `retryCount < 3` | `retryCount < 3` (OutboxEvent.java:97) | ✅ |
| `resetForRetry()` | `retryCount < 3` | `retryCount < 3` (OutboxEvent.java:90) | ✅ |

**파일**: `/Users/stw/Dev/project/Live-Commerce/common-lib/src/main/java/com/live_commerce/common/outbox/OutboxEvent.java`

**Result**: ✅ 완전 일치 (5에서 3으로 수정 완료)

---

### 항목 7: OutboxEventPublisher 동기 발행(kafkaTemplate.send().get())

| 항목 | Design | Implementation | Status |
|------|--------|---------------|--------|
| 동기 발행 | `send().get(5, TimeUnit.SECONDS)` | OutboxEventPublisher.java:57 `.get(5, TimeUnit.SECONDS)` | ✅ |
| updateStatus 분리 | `@Transactional public void updateStatus()` | OutboxEventPublisher.java:71-80 `@Transactional public void updateEventStatus()` | ✅ |
| 5초 폴링 | `@Scheduled(fixedDelay = 5000)` | OutboxEventPublisher.java:33 `@Scheduled(fixedDelay = 5000)` | ✅ |
| FAILED 재시도 로직 | Design에 명시적 언급 없음 | OutboxEventPublisher.java:85-104 `retryFailedEvents()` | ✅ (추가 구현) |
| 오래된 이벤트 정리 | Design에 명시적 언급 없음 | OutboxEventPublisher.java:109-115 `cleanUpOldEvents()` | ✅ (추가 구현) |

**파일**: `/Users/stw/Dev/project/Live-Commerce/common-lib/src/main/java/com/live_commerce/common/outbox/OutboxEventPublisher.java`

**Result**: ✅ Design 이상으로 구현 (추가 기능: 재시도 로직, 이벤트 정리)

---

### 항목 8: OutboxEventHelper 신규 생성

| 항목 | Design | Implementation | Status |
|------|--------|---------------|--------|
| 클래스 존재 | 신규 생성 | OutboxEventHelper.java 존재 | ✅ |
| 위치 | `order/infrastructure/outbox/` | 동일 | ✅ |
| ObjectMapper 의존 | 있음 | 있음 (OutboxEventHelper.java:23) | ✅ |
| OutboxEventRepository 의존 | 있음 | 있음 (OutboxEventHelper.java:24) | ✅ |
| @Transactional | Design에 명시 | 구현에 **없음** | ⚠️ |
| saveEvent 메서드 시그니처 | `(String, UUID, String, String, Object)` | `(String, UUID, String, String, Object)` | ✅ |

**세부 분석**:
- Design에서는 `@Transactional // 호출자의 트랜잭션에 참여`를 명시했으나, 실제 구현에는 `@Transactional` 어노테이션이 없다.
- 다만 호출하는 쪽(PaymentFailureServiceKafka.handlePaymentFailure())이 `@Transactional`이므로 Spring의 트랜잭션 전파 기본값(REQUIRED)에 의해 동일 트랜잭션에 참여한다. 기능적으로는 동일하게 동작하지만, 독립 호출 시 트랜잭션 보장이 안 될 수 있다.

**파일**: `/Users/stw/Dev/project/Live-Commerce/order/src/main/java/com/live_commerce/order/infrastructure/outbox/OutboxEventHelper.java`

**Result**: ⚠️ 기능적 동작은 동일하나 `@Transactional` 어노테이션 누락

---

### 항목 9: PaymentFailureServiceKafka에서 Outbox로 이벤트 저장 (직접 Kafka 발행 제거)

| 항목 | Design | Implementation | Status |
|------|--------|---------------|--------|
| Kafka 직접 발행 제거 | InventoryEventProducer, KafkaTemplate 제거 | 의존성에서 제거됨 ✅ | ✅ |
| outboxEventHelper.saveEvent (INVENTORY_ROLLBACK) | 있음 | PaymentFailureServiceKafka.java:73-74 | ✅ |
| outboxEventHelper.saveEvent (ORDER_FAILED) | 있음 | PaymentFailureServiceKafka.java:81-82 | ✅ |
| 멱등성 체크 | 있음 (이미 FAILED인 주문 early return) | PaymentFailureServiceKafka.java:53-56 | ✅ |

**파일**: `/Users/stw/Dev/project/Live-Commerce/order/src/main/java/com/live_commerce/order/kafkaOrder/service/PaymentFailureServiceKafka.java`

**Result**: ✅ 완전 일치 (Design 의도대로 Outbox 패턴 적용)

---

## 4. Phase 3: Saga State

### 항목 10: OrderCreateServiceKafka에서 SagaState 생성

| 항목 | Design | Implementation | Status |
|------|--------|---------------|--------|
| SagaState.start() 호출 | `SagaState.start("ORDER_CREATION", orderId, ...)` | OrderCreateServiceKafka.java:178 `SagaState.start("ORDER_CREATION", orderId, null)` | ✅ |
| 호출 위치 | 주문 저장 직후 | 3곳의 주문 저장 후 `createSagaState(savedOrder.getId())` 호출 (Line 103, 119, 169) | ✅ |
| sagaStateRepository.save() | 있음 | OrderCreateServiceKafka.java:179 | ✅ |

**파일**: `/Users/stw/Dev/project/Live-Commerce/order/src/main/java/com/live_commerce/order/kafkaOrder/service/OrderCreateServiceKafka.java`

**Result**: ✅ 완전 일치

---

### 항목 11: PaymentFailureServiceKafka에서 SagaState fail() + startCompensation()

| 항목 | Design | Implementation | Status |
|------|--------|---------------|--------|
| saga.fail(message) | 있음 | PaymentFailureServiceKafka.java:94 `saga.fail(failureMessage)` | ✅ |
| saga.startCompensation() | 있음 | PaymentFailureServiceKafka.java:95 `saga.startCompensation()` | ✅ |
| sagaStateRepository.save() | 있음 | PaymentFailureServiceKafka.java:96 | ✅ |
| findByAggregateId 조회 | 있음 | PaymentFailureServiceKafka.java:93 `.ifPresent()` | ✅ |

**Result**: ✅ 완전 일치

---

### 항목 12: PaymentSuccessServiceKafka에서 SagaState complete()

| 항목 | Design | Implementation | Status |
|------|--------|---------------|--------|
| saga.complete() | 있음 | PaymentSuccessServiceKafka.java:76 `saga.complete()` | ✅ |
| sagaStateRepository.save() | 있음 | PaymentSuccessServiceKafka.java:77 | ✅ |
| findByAggregateId 조회 | 있음 | PaymentSuccessServiceKafka.java:75 `.ifPresent()` | ✅ |

**주의사항**: PaymentSuccessServiceKafka는 `InventoryEventProducer`를 여전히 직접 사용하고 있다 (Line 53-54). Design 클래스 다이어그램에서 PaymentFailureServiceKafka의 `InventoryEventProducer` 제거는 명시했으나, PaymentSuccessServiceKafka에 대해서는 Outbox 전환을 명시하지 않았다. 현재 구현은 Design과 일치한다.

**파일**: `/Users/stw/Dev/project/Live-Commerce/order/src/main/java/com/live_commerce/order/kafkaOrder/service/PaymentSuccessServiceKafka.java`

**Result**: ✅ 완전 일치

---

### 항목 13: SagaAdminController 신규 생성 및 4개 API

| API | Design | Implementation | Status |
|-----|--------|---------------|--------|
| `GET /api/v1/admin/saga/{orderId}` | Saga 상태 조회 | SagaAdminController.java:30 | ✅ |
| `POST /api/v1/admin/saga/{orderId}/compensated` | 보상 완료 처리 (수동) | SagaAdminController.java:38 | ✅ |
| `GET /api/v1/admin/saga/failed` | 실패 중인 Saga 목록 조회 | SagaAdminController.java:48 | ✅ |
| `GET /api/v1/admin/saga/compensating` | 보상 중인 Saga 목록 조회 | SagaAdminController.java:54 | ✅ |

**세부 분석**:
- Design에서는 `GET /api/v1/admin/saga/{orderId}` 1개 API만 명시적으로 정의함.
- 구현에서는 총 4개 API를 제공하여 Design 이상으로 구현함.
- Swagger `@Tag`, `@Operation` 어노테이션도 적용되어 문서화 완료.

**파일**: `/Users/stw/Dev/project/Live-Commerce/order/src/main/java/com/live_commerce/order/presentation/controller/SagaAdminController.java`

**Result**: ✅ Design 이상으로 구현 (1개 API -> 4개 API)

---

## 5. Phase 4: Coupon DLQ

### 항목 14: coupon KafkaConfig에 ExponentialBackOff + DLQ 설정

| 항목 | Design | Implementation | Status |
|------|--------|---------------|--------|
| ExponentialBackOff(1000L, 2.0) | 있음 | KafkaConfig.java:60 | ✅ |
| setMaxElapsedTime(10000L) | 있음 | KafkaConfig.java:61 | ✅ |
| DefaultErrorHandler + DLQ 전송 | 있음 | KafkaConfig.java:63-71 | ✅ |
| AckMode.MANUAL_IMMEDIATE | 있음 | KafkaConfig.java:57 | ✅ |
| ConsumerFactory 설정 | Design에 없음 | KafkaConfig.java:36-45 | ✅ (추가 구현) |
| NotRetryableExceptions 설정 | Design에 없음 | KafkaConfig.java:76-79 | ✅ (추가 구현) |

**파일**: `/Users/stw/Dev/project/Live-Commerce/coupon/src/main/java/com/live_commerce/coupon/infrastructure/config/KafkaConfig.java`

**Result**: ✅ Design 이상으로 구현 (ConsumerFactory, NotRetryableExceptions 추가)

---

### 항목 15: OrderFailedDLQConsumer 신규 생성

| 항목 | Design | Implementation | Status |
|------|--------|---------------|--------|
| 클래스 존재 | 신규 생성 | OrderFailedDLQConsumer.java 존재 | ✅ |
| @KafkaListener(topics = "order-failed.DLQ") | 있음 | OrderFailedDLQConsumer.java:24 | ✅ |
| groupId = "${spring.application.name}-dlq" | 있음 | OrderFailedDLQConsumer.java:25 | ✅ |
| log.error 로깅 | 있음 | OrderFailedDLQConsumer.java:33-44 | ✅ |
| ack.acknowledge() | 있음 | OrderFailedDLQConsumer.java:49-51 | ✅ |
| event-schema import 사용 | `com.live_commerce.events.order.OrderFailedEvent` | OrderFailedDLQConsumer.java:3 | ✅ |

**추가 구현**: `@Header(KafkaHeaders.RECEIVED_TOPIC)`, `@Header(KafkaHeaders.OFFSET)` 파라미터로 더 상세한 로깅 제공.

**파일**: `/Users/stw/Dev/project/Live-Commerce/coupon/src/main/java/com/live_commerce/coupon/infrastructure/kafka/consumer/OrderFailedDLQConsumer.java`

**Result**: ✅ Design 이상으로 구현

---

### 항목 16: coupon OrderFailedEventConsumer가 event-schema import 사용

| 항목 | Design | Implementation | Status |
|------|--------|---------------|--------|
| import 변경 | `com.live_commerce.events.order.OrderFailedEvent` | OrderFailedEventConsumer.java:4 동일 import 확인 | ✅ |

**Result**: ✅ 완전 일치

---

## 6. DB Schema

### 항목 17: outbox_event 테이블 마이그레이션 스크립트

| 항목 | Design | Implementation | Status |
|------|--------|---------------|--------|
| 테이블명 | outbox_event | V2 스크립트:12 `CREATE TABLE IF NOT EXISTS outbox_event` | ✅ |
| id (UUID PK) | 있음 | ✅ | ✅ |
| aggregate_type VARCHAR(50) | 있음 | ✅ | ✅ |
| aggregate_id UUID | 있음 | ✅ | ✅ |
| event_type VARCHAR(100) | 있음 | ✅ | ✅ |
| topic VARCHAR(100) | 있음 | ✅ | ✅ |
| payload TEXT | 있음 | ✅ | ✅ |
| status VARCHAR(20) DEFAULT 'PENDING' | 있음 | ✅ | ✅ |
| retry_count INT DEFAULT 0 | 있음 | ✅ | ✅ |
| error_message TEXT | 있음 | ✅ | ✅ |
| created_at TIMESTAMP DEFAULT NOW() | 있음 | ✅ | ✅ |
| published_at TIMESTAMP | 있음 | ✅ | ✅ |
| idx_outbox_status 인덱스 | 있음 | ✅ | ✅ |
| idx_outbox_created_at 인덱스 | 있음 | ✅ | ✅ |
| SET search_path TO orders | 있음 | V2 스크립트:6 | ✅ |
| COMMENT ON TABLE/COLUMN | Design에 없음 | V2 스크립트:29-30 | ✅ (추가) |

**파일**: `/Users/stw/Dev/project/Live-Commerce/order/src/main/resources/db/migration/V2__add_outbox_and_saga_tables.sql`

**Result**: ✅ Design 이상으로 구현 (COMMENT 추가)

---

### 항목 18: saga_state 테이블 마이그레이션 스크립트

| 항목 | Design | Implementation | Status |
|------|--------|---------------|--------|
| 테이블명 | saga_state | V2 스크립트:36 `CREATE TABLE IF NOT EXISTS saga_state` | ✅ |
| saga_id (UUID PK) | 있음 | ✅ | ✅ |
| saga_type VARCHAR(50) | 있음 | ✅ | ✅ |
| aggregate_id UUID UNIQUE | 있음 | ✅ | ✅ |
| status VARCHAR(20) | 있음 | ✅ | ✅ |
| current_step VARCHAR(50) DEFAULT 'INIT' | 있음 | ✅ | ✅ |
| payload TEXT | 있음 | ✅ | ✅ |
| error_message TEXT | 있음 | ✅ | ✅ |
| retry_count INT DEFAULT 0 | 있음 | ✅ | ✅ |
| started_at TIMESTAMP DEFAULT NOW() | 있음 | ✅ | ✅ |
| completed_at TIMESTAMP | 있음 | ✅ | ✅ |
| idx_saga_aggregate_id 인덱스 | 있음 | ✅ | ✅ |
| idx_saga_status 인덱스 | 있음 | ✅ | ✅ |
| COMMENT ON TABLE/COLUMN | Design에 없음 | V2 스크립트:52-54 | ✅ (추가) |

**Result**: ✅ Design 이상으로 구현 (COMMENT 추가)

---

## 7. Overall Scores

### 7.1 항목별 결과 요약

| # | 항목 | Status | 비고 |
|---|------|--------|------|
| 1 | settings.gradle에 common-lib, event-schema include | ✅ | 완전 일치 |
| 2 | event-schema 이벤트 필드 정렬 | ⚠️ | PaymentCompletedEvent, PaymentFailedEvent 필드 불일치 |
| 3 | InventoryDecreaseRequestEvent 신규 추가 | ✅ | 구현 완료 |
| 4 | 서비스별 build.gradle event-schema 의존성 | ✅ | 4/4 서비스 완료 |
| 5 | common-lib build.gradle JPA, Kafka 의존성 | ✅ | 필수 의존성 모두 포함 |
| 6 | OutboxEvent retryCount 임계값 3 | ✅ | 5에서 3으로 수정 완료 |
| 7 | OutboxEventPublisher 동기 발행 | ✅ | send().get() 적용 + 추가 기능 |
| 8 | OutboxEventHelper 신규 생성 | ⚠️ | @Transactional 어노테이션 누락 |
| 9 | PaymentFailureServiceKafka Outbox 전환 | ✅ | 직접 Kafka 발행 제거, Outbox 저장 |
| 10 | OrderCreateServiceKafka SagaState 생성 | ✅ | 모든 주문 저장 경로에서 호출 |
| 11 | PaymentFailureServiceKafka SagaState fail/startCompensation | ✅ | 완전 일치 |
| 12 | PaymentSuccessServiceKafka SagaState complete | ✅ | 완전 일치 |
| 13 | SagaAdminController 4개 API | ✅ | Design(1개) 이상 구현(4개) |
| 14 | coupon KafkaConfig ExponentialBackOff + DLQ | ✅ | 추가 설정 포함 |
| 15 | OrderFailedDLQConsumer 신규 생성 | ✅ | 상세 로깅 추가 |
| 16 | coupon OrderFailedEventConsumer event-schema import | ✅ | 완전 일치 |
| 17 | outbox_event 테이블 마이그레이션 | ✅ | COMMENT 추가 |
| 18 | saga_state 테이블 마이그레이션 | ✅ | COMMENT 추가 |

### 7.2 Match Rate Summary

```
+---------------------------------------------+
|  Overall Match Rate: 94.4%                   |
+---------------------------------------------+
|  Total Items:        18                      |
|  Complete Match:     16 items (88.9%)        |
|  Partial Match:       2 items (11.1%)        |
|  Not Implemented:     0 items (0.0%)         |
+---------------------------------------------+
```

### 7.3 Category Scores

| Category | Score | Status |
|----------|:-----:|:------:|
| Phase 1: event-schema 통합 | 87.5% | ⚠️ (이벤트 필드 불일치 2건) |
| Phase 2: Outbox Pattern | 96% | ✅ (@Transactional 미세 차이) |
| Phase 3: Saga State | 100% | ✅ |
| Phase 4: Coupon DLQ | 100% | ✅ |
| DB Schema | 100% | ✅ |
| **Overall** | **94.4%** | ✅ |

---

## 8. Differences Found

### 8-1. Changed Features (Design != Implementation)

| # | Item | Design | Implementation | Impact |
|---|------|--------|----------------|--------|
| 1 | PaymentCompletedEvent 필드 | `(orderId, paymentId, amount, completedAt)` | `(orderId, message, finalPaidPrice)` | Medium - 이벤트 스키마가 다르므로 Design 문서 업데이트 필요 |
| 2 | PaymentFailedEvent 필드 | `(orderId, reason, failedAt)` | `(orderId, message)` | Low - `reason`을 `message`로 명명, `failedAt` 생략은 의도적 간소화 가능 |
| 3 | OutboxEventHelper @Transactional | 메서드에 `@Transactional` 명시 | `@Transactional` 없음 | Low - 호출자 트랜잭션 전파로 기능적 동일, 독립 호출 시 위험 |

### 8-2. Added Features (Design X, Implementation O)

| # | Item | Implementation Location | Description |
|---|------|------------------------|-------------|
| 1 | OutboxEventPublisher.retryFailedEvents() | common-lib OutboxEventPublisher.java:85-104 | 1분마다 FAILED 이벤트 재시도 |
| 2 | OutboxEventPublisher.cleanUpOldEvents() | common-lib OutboxEventPublisher.java:109-115 | 매일 7일 이상 PUBLISHED 이벤트 정리 |
| 3 | KafkaConfig.addNotRetryableExceptions() | coupon KafkaConfig.java:76-79 | 재시도 불가 예외 설정 |
| 4 | KafkaConfig.ConsumerFactory Bean | coupon KafkaConfig.java:36-45 | ConsumerFactory 커스텀 설정 |
| 5 | SQL COMMENT ON TABLE/COLUMN | V2 migration SQL:29-30, 52-54 | 테이블/컬럼 설명 추가 |
| 6 | SagaAdminController 추가 API 3개 | SagaAdminController.java:37-56 | 보상 완료, 실패 목록, 보상중 목록 API |

---

## 9. Recommended Actions

### 9.1 Immediate (Documentation Update)

| Priority | Item | Action |
|----------|------|--------|
| 1 | PaymentCompletedEvent 필드 불일치 | Design 문서의 Section 2-2를 `(orderId, message, finalPaidPrice)`로 수정 |
| 2 | PaymentFailedEvent 필드 불일치 | Design 문서의 Section 2-2를 `(orderId, message)`로 수정 |

### 9.2 Short-term (Code Improvement)

| Priority | Item | File | Expected Impact |
|----------|------|------|-----------------|
| 1 | OutboxEventHelper에 @Transactional 추가 | order/.../OutboxEventHelper.java | 독립 호출 시 트랜잭션 안전성 확보 |

### 9.3 Design Document Update Items

Design 문서에 반영해야 할 추가 구현 사항:

- [ ] OutboxEventPublisher의 retryFailedEvents(), cleanUpOldEvents() 메서드 추가 설명
- [ ] SagaAdminController 4개 API 전체 명세 추가
- [ ] Coupon KafkaConfig의 NotRetryableExceptions, ConsumerFactory 설정 설명
- [ ] PaymentCompletedEvent, PaymentFailedEvent 필드 정의 수정

---

## 10. Conclusion

Match Rate **94.4%** 로 Design과 Implementation이 높은 수준에서 일치한다.

주요 차이점은 모두 영향도가 Low~Medium 수준이며:

1. **이벤트 필드 불일치** (2건) - 기존 결제 서비스와의 호환을 위해 Design 시점과 달라진 것으로 판단. Design 문서 업데이트로 해결.
2. **@Transactional 누락** (1건) - 기능적으로는 동일 동작하나 방어적 프로그래밍 관점에서 추가 권장.
3. **추가 구현** (6건) - Design 범위를 초과하여 운영 편의성을 위한 기능이 추가됨. 긍정적 Gap.

**Synchronization 권장**: Option 2 - Design 문서를 Implementation에 맞추어 업데이트

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0 | 2026-02-13 | Initial gap analysis | gap-detector (Claude Code) |
