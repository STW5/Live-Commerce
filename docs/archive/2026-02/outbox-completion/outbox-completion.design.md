# Design: outbox-completion

> Order / Coupon / Payment 서비스 남은 kafkaTemplate 직접 호출 → Outbox 패턴 전환

**작성일**: 2026-02-28
**참조 Plan**: `docs/01-plan/features/outbox-completion.plan.md`
**영향 모듈**: `order`, `coupon`, `payment`

---

## 1. As-Is 현황

### 1.1 직접 호출 3곳

```
Outbox 패턴 완성 현황:

Order ──[inventory-decrease,  Outbox]──▶ Product  ✅
Order ──[inventory-rollback,  Outbox]──▶ Product  ✅
Order ──[order-failed,        Outbox]──▶ Payment  ✅
Product ─[inventory-decreased, Outbox]──▶ Order   ✅
Product ─[inventory-sold-out,  Outbox]──▶ All     ✅

Order ──[coupon-used,         직접]──▶ Coupon     ⚠️  ← Sub-1
Coupon ─[coupon-used,         직접]──▶ (수신자)   ⚠️  ← Sub-2
Payment─[payment-completed,   직접]──▶ Order      ⚠️  ← Sub-3
Payment─[payment-failed,      직접]──▶ Order      ⚠️  ← Sub-3
```

### 1.2 파일별 현황

**[Sub-1] KafkaOrderEventPublisher.java**
```java
// order/adapter/out/messaging/KafkaOrderEventPublisher.java
private final KafkaTemplate<String, Object> kafkaTemplate;  // 직접 호출용
private final OutboxEventHelper outboxEventHelper;           // 이미 있음

@Override
public void publishCouponUsed(UUID couponId, UUID userId) {
    CouponUsedEvent event = new CouponUsedEvent(couponId, userId);
    kafkaTemplate.send("coupon-used", userId.toString(), event);  // ⚠️
}
```

**[Sub-2] KafkaCouponEventPublisher.java**
```java
// coupon/adapter/out/messaging/KafkaCouponEventPublisher.java
private final KafkaTemplate<String, Object> kafkaTemplate;  // 직접 호출만 있음

@Override
public void publishCouponUsedEvent(UUID couponId, UUID userId) {
    CouponUsedEvent event = new CouponUsedEvent(couponId, userId);
    kafkaTemplate.send("coupon-used", event);  // ⚠️
}
```

**[Sub-3] KafkaEventPublisherAdapter.java + PaymentEventProducer.java**
```java
// payment/infrastructure/adapter/event/KafkaEventPublisherAdapter.java
private final PaymentEventProducer paymentEventProducer;

@Override
public void publishCompleted(PaymentCompletedEvent event) {
    paymentEventProducer.sendPaymentCompleted(
        com.live_commerce.events.payment.PaymentCompletedEvent.of(...));  // ⚠️ 간접 직접호출
}

// payment/infrastructure/kafka/producer/PaymentEventProducer.java
private final KafkaTemplate<String, Object> kafkaTemplate;

public void sendPaymentCompleted(PaymentCompletedEvent event) {
    send(COMPLETED_TOPIC, event.orderId().toString(), event);  // ⚠️
}
```

---

## 2. To-Be 설계

### 2.1 서비스별 변경 전략

| 서비스 | 전략 | Outbox 인프라 |
|--------|------|--------------|
| **Order** | `KafkaOrderEventPublisher`에서 기존 `OutboxEventHelper` 사용 | 이미 있음 |
| **Coupon** | `build.gradle` + `OutboxEventHelper` 신규 + Publisher 수정 | 신규 생성 |
| **Payment** | `KafkaEventPublisherAdapter` → `OutboxEventHelper` 직접 사용, `PaymentEventProducer` 제거 | 신규 생성 |

### 2.2 Payment ADR: KafkaEventPublisherAdapter 개선

`PaymentEventProducer`는 `KafkaEventPublisherAdapter`에서만 사용되는 불필요한 중간 계층.
Outbox 전환 시 `KafkaEventPublisherAdapter`에서 직접 `OutboxEventHelper`를 사용하고
`PaymentEventProducer`는 삭제한다.

```
Before: KafkaEventPublisherAdapter → PaymentEventProducer → kafkaTemplate.send()
After:  KafkaEventPublisherAdapter → OutboxEventHelper.saveEvent()
```

---

## 3. 컴포넌트별 변경 명세

### 3.1 Sub-1: Order — KafkaOrderEventPublisher

**경로**: `order/src/main/java/com/live_commerce/order/adapter/out/messaging/KafkaOrderEventPublisher.java`

**Before:**
```java
@Component
@RequiredArgsConstructor
public class KafkaOrderEventPublisher implements OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;  // ← 제거
    private final OutboxEventHelper outboxEventHelper;

    @Override
    public void publishCouponUsed(UUID couponId, UUID userId) {
        CouponUsedEvent event = new CouponUsedEvent(couponId, userId);
        kafkaTemplate.send("coupon-used", userId.toString(), event);  // ← 제거
        log.info("[KafkaOrderEventPublisher] 쿠폰 사용 이벤트 발행 - couponId: {}", couponId);
    }

    // publishInventoryDecrease, publishInventoryRollback, publishOrderFailed
    // → 이미 Outbox 패턴 (변경 없음)
}
```

**After:**
```java
@Component
@RequiredArgsConstructor
public class KafkaOrderEventPublisher implements OrderEventPublisher {

    // KafkaTemplate 필드 제거 (다른 메서드도 Outbox 사용하므로 불필요)
    private final OutboxEventHelper outboxEventHelper;

    @Override
    public void publishCouponUsed(UUID couponId, UUID userId) {
        CouponUsedEvent event = new CouponUsedEvent(couponId, userId);
        outboxEventHelper.saveEvent("ORDER", couponId,              // ← Outbox로 전환
                "COUPON_USED", "coupon-used", event);
        log.info("[KafkaOrderEventPublisher] 쿠폰 사용 이벤트 Outbox 저장 - couponId: {}", couponId);
    }
}
```

**변경 요약:**
- `KafkaTemplate` 필드 제거 (import 포함)
- `kafkaTemplate.send()` → `outboxEventHelper.saveEvent()` 교체
- `aggregateId`: `couponId` 사용 (쿠폰이 집합체)

---

### 3.2 Sub-2: Coupon — build.gradle + OutboxEventHelper + Publisher

#### 3.2.1 coupon/build.gradle

```groovy
// 기존
implementation project(':event-schema')

// 추가
// common-lib (Outbox Pattern)
implementation project(':common-lib')
```

#### 3.2.2 신규: OutboxEventHelper.java

**경로**: `coupon/src/main/java/com/live_commerce/coupon/adapter/out/outbox/OutboxEventHelper.java`

```java
package com.live_commerce.coupon.adapter.out.outbox;

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
 * Coupon 서비스 Outbox 이벤트 저장 헬퍼
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

#### 3.2.3 KafkaCouponEventPublisher.java

**경로**: `coupon/src/main/java/com/live_commerce/coupon/adapter/out/messaging/KafkaCouponEventPublisher.java`

**Before:**
```java
@Component
@RequiredArgsConstructor
public class KafkaCouponEventPublisher implements CouponEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;  // ← 제거

    @Override
    public void publishCouponUsedEvent(UUID couponId, UUID userId) {
        CouponUsedEvent event = new CouponUsedEvent(couponId, userId);
        kafkaTemplate.send("coupon-used", event);  // ← 제거
        log.info("[KafkaCouponEventPublisher] 쿠폰 사용 이벤트 발행: couponId={}, userId={}", couponId, userId);
    }
}
```

**After:**
```java
@Component
@RequiredArgsConstructor
public class KafkaCouponEventPublisher implements CouponEventPublisher {

    private final OutboxEventHelper outboxEventHelper;  // ← 추가

    @Override
    public void publishCouponUsedEvent(UUID couponId, UUID userId) {
        CouponUsedEvent event = new CouponUsedEvent(couponId, userId);
        outboxEventHelper.saveEvent("COUPON", couponId,             // ← Outbox로 전환
                "COUPON_USED", "coupon-used", event);
        log.info("[KafkaCouponEventPublisher] 쿠폰 사용 이벤트 Outbox 저장: couponId={}", couponId);
    }
}
```

---

### 3.3 Sub-3: Payment — OutboxEventHelper + KafkaEventPublisherAdapter

#### 3.3.1 신규: OutboxEventHelper.java

**경로**: `payment/src/main/java/com/live_commerce/payment/infrastructure/outbox/OutboxEventHelper.java`

> common-lib이 이미 있으므로 build.gradle 수정 불필요.

```java
package com.live_commerce.payment.infrastructure.outbox;

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
 * Payment 서비스 Outbox 이벤트 저장 헬퍼
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

#### 3.3.2 KafkaEventPublisherAdapter.java

**경로**: `payment/src/main/java/com/live_commerce/payment/infrastructure/adapter/event/KafkaEventPublisherAdapter.java`

**Before:**
```java
@Component
@RequiredArgsConstructor
public class KafkaEventPublisherAdapter implements PublishPaymentEventPort {

    private final PaymentEventProducer paymentEventProducer;  // ← 제거

    @Override
    public void publishCompleted(PaymentCompletedEvent event) {
        paymentEventProducer.sendPaymentCompleted(
            com.live_commerce.events.payment.PaymentCompletedEvent.of(
                event.orderId(), event.message(), event.amount()));  // ← 제거
    }

    @Override
    public void publishFailed(PaymentFailedEvent event) {
        paymentEventProducer.sendPaymentFailed(
            com.live_commerce.events.payment.PaymentFailedEvent.of(
                event.orderId(), event.message()));  // ← 제거
    }
}
```

**After:**
```java
@Component
@RequiredArgsConstructor
public class KafkaEventPublisherAdapter implements PublishPaymentEventPort {

    private final OutboxEventHelper outboxEventHelper;  // ← 추가

    @Override
    public void publishCompleted(PaymentCompletedEvent event) {
        com.live_commerce.events.payment.PaymentCompletedEvent kafkaEvent =
            com.live_commerce.events.payment.PaymentCompletedEvent.of(
                event.orderId(), event.message(), event.amount());
        outboxEventHelper.saveEvent("PAYMENT", event.orderId(),         // ← Outbox로 전환
                "PAYMENT_COMPLETED", "payment-completed", kafkaEvent);
        log.info("[KafkaEventPublisherAdapter] payment-completed Outbox 저장 - orderId: {}", event.orderId());
    }

    @Override
    public void publishFailed(PaymentFailedEvent event) {
        com.live_commerce.events.payment.PaymentFailedEvent kafkaEvent =
            com.live_commerce.events.payment.PaymentFailedEvent.of(
                event.orderId(), event.message());
        outboxEventHelper.saveEvent("PAYMENT", event.orderId(),         // ← Outbox로 전환
                "PAYMENT_FAILED", "payment-failed", kafkaEvent);
        log.info("[KafkaEventPublisherAdapter] payment-failed Outbox 저장 - orderId: {}", event.orderId());
    }
}
```

#### 3.3.3 PaymentEventProducer.java 삭제

`PaymentEventProducer`는 `KafkaEventPublisherAdapter`에서만 사용.
Outbox 전환 후 더 이상 참조 없음 → 파일 삭제.

**삭제 경로**: `payment/src/main/java/com/live_commerce/payment/infrastructure/kafka/producer/PaymentEventProducer.java`

---

## 4. 전체 변경 파일 요약

| 서비스 | 파일 | 변경 유형 | 핵심 변경 |
|--------|------|----------|----------|
| `order` | `KafkaOrderEventPublisher.java` | 수정 | KafkaTemplate 제거, publishCouponUsed → Outbox |
| `coupon` | `build.gradle` | 수정 | common-lib 의존성 추가 |
| `coupon` | `OutboxEventHelper.java` | 신규 | adapter.out.outbox 패키지 |
| `coupon` | `KafkaCouponEventPublisher.java` | 수정 | KafkaTemplate 제거, publishCouponUsedEvent → Outbox |
| `payment` | `OutboxEventHelper.java` | 신규 | infrastructure.outbox 패키지 |
| `payment` | `KafkaEventPublisherAdapter.java` | 수정 | PaymentEventProducer 제거, Outbox 직접 사용 |
| `payment` | `PaymentEventProducer.java` | **삭제** | 더 이상 사용 없음 |

---

## 5. ADR (Architecture Decision Records)

### ADR-1: Order의 aggregateId는 couponId 사용

**결정**: `publishCouponUsed()`에서 `aggregateId = couponId`

**이유**:
- `coupon-used` 이벤트의 집합체는 Coupon
- orderId는 `publishCouponUsed()` 시그니처에 없음
- 포트 인터페이스 변경 없이 최소 수정

**대안**: orderId를 시그니처에 추가 → 포트 인터페이스, 서비스 레이어 연쇄 변경 필요 (over-engineering)

### ADR-2: Payment - PaymentEventProducer 삭제

**결정**: Outbox 전환 시 `PaymentEventProducer`를 삭제

**이유**:
- `KafkaEventPublisherAdapter`에서만 사용 (grep으로 확인)
- Outbox 전환 후 kafkaTemplate 직접 의존성 완전 제거 목적
- 불필요한 중간 계층 제거로 코드 단순화

**대안**: PaymentEventProducer 유지 → 미래에 다시 kafkaTemplate 직접 호출 유혹 발생, 불필요한 복잡성 유지

### ADR-3: Coupon OutboxEventHelper 패키지 위치

**결정**: `coupon.adapter.out.outbox.OutboxEventHelper`

**이유**:
- Coupon 서비스는 헥사고날 아키텍처 적용됨
- Outbox 저장 헬퍼는 아웃바운드 어댑터이므로 `adapter/out/outbox` 패키지가 정확한 위치
- Order/Product 서비스와 다른 위치 (Order: `infrastructure.outbox`, Product: `product.infrastructure.outbox`)
- 헥사고날 컨벤션에 맞게 `adapter.out.outbox` 사용

### ADR-4: Payment OutboxEventHelper 패키지 위치

**결정**: `payment.infrastructure.outbox.OutboxEventHelper`

**이유**:
- Payment 서비스는 부분 헥사고날 구조 (`infrastructure.adapter.event` 계층이 혼재)
- `KafkaEventPublisherAdapter`가 `infrastructure.adapter.event`에 있으므로 OutboxHelper는 `infrastructure.outbox`가 적합
- common-lib 이미 있으므로 build.gradle 수정 불필요

---

## 6. 구현 순서

```
Step 1. Order - KafkaOrderEventPublisher 수정 (3줄 변경, 검증 빠름)
         - KafkaTemplate import/필드 제거
         - kafkaTemplate.send() → outboxEventHelper.saveEvent() 교체

Step 2. Coupon - build.gradle + OutboxEventHelper 생성 + Publisher 수정
         - build.gradle: common-lib 추가
         - OutboxEventHelper.java 신규 생성
         - KafkaCouponEventPublisher: KafkaTemplate → OutboxEventHelper

Step 3. Payment - OutboxEventHelper 생성 + Adapter 수정 + Producer 삭제
         - OutboxEventHelper.java 신규 생성
         - KafkaEventPublisherAdapter: PaymentEventProducer → OutboxEventHelper
         - PaymentEventProducer.java 삭제

Step 4. 빌드 검증
         ./gradlew :order:compileJava :coupon:compileJava :payment:compileJava
```

---

## 7. 완료 기준 (체크리스트)

**Order**
- [ ] `KafkaOrderEventPublisher`의 `KafkaTemplate` 필드/import 제거
- [ ] `publishCouponUsed()` → `outboxEventHelper.saveEvent("ORDER", couponId, "COUPON_USED", "coupon-used", event)`

**Coupon**
- [ ] `coupon/build.gradle`에 `implementation project(':common-lib')` 추가
- [ ] `OutboxEventHelper.java` 생성 (`coupon.adapter.out.outbox` 패키지)
- [ ] `KafkaCouponEventPublisher`의 `KafkaTemplate` 필드/import 제거
- [ ] `publishCouponUsedEvent()` → `outboxEventHelper.saveEvent("COUPON", couponId, "COUPON_USED", "coupon-used", event)`

**Payment**
- [ ] `OutboxEventHelper.java` 생성 (`payment.infrastructure.outbox` 패키지)
- [ ] `KafkaEventPublisherAdapter`의 `PaymentEventProducer` 필드/import 제거, `OutboxEventHelper` 추가
- [ ] `publishCompleted()` → `outboxEventHelper.saveEvent("PAYMENT", orderId, "PAYMENT_COMPLETED", ...)`
- [ ] `publishFailed()` → `outboxEventHelper.saveEvent("PAYMENT", orderId, "PAYMENT_FAILED", ...)`
- [ ] `PaymentEventProducer.java` 삭제

**빌드**
- [ ] `./gradlew :order:compileJava :coupon:compileJava :payment:compileJava` 성공
