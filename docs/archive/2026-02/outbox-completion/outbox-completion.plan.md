# Plan: outbox-completion

## 개요

| 항목 | 내용 |
|------|------|
| Feature | outbox-completion |
| 작성일 | 2026-02-28 |
| 우선순위 | P1 (Saga 신뢰성 완성) |
| 예상 복잡도 | 낮음~중간 (3개 서브 작업) |
| 담당 서비스 | order, coupon, payment |

## 배경 및 목적

Outbox 패턴 전환 시리즈:
- ✅ Phase 1: `compensation-transaction` — Order 서비스 보상 이벤트 Outbox 전환
- ✅ Phase 2: `product-inventory-outbox` — Product 서비스 inventory 이벤트 Outbox 전환
- 🎯 Phase 3 (이번): 남은 3곳의 `kafkaTemplate.send()` 직접 호출 Outbox 전환

### 현재 직접 호출 잔존 위치

| 서비스 | 파일 | 이벤트 | 위험도 |
|--------|------|--------|--------|
| **Payment** | `PaymentEventProducer` | `payment-completed`, `payment-failed` | 🔴 높음 (Saga 핵심) |
| **Order** | `KafkaOrderEventPublisher.publishCouponUsed()` | `coupon-used` | 🟡 중간 |
| **Coupon** | `KafkaCouponEventPublisher.publishCouponUsedEvent()` | `coupon-used` | 🟡 중간 |

### 위험 시나리오

**Payment (가장 치명적)**:
```
결제 승인 → DB 커밋 성공 → kafkaTemplate.send("payment-completed") 실패
→ Order 서비스가 응답 못 받고 주문이 PENDING 상태로 멈춤
→ 재고는 차감됐고 결제는 됐지만 주문 완료 처리 안 됨
```

**Order coupon-used**:
```
주문 완료 → kafkaTemplate.send("coupon-used") 실패
→ 쿠폰이 사용된 것으로 Coupon 서비스에 전달 안 됨
→ 같은 쿠폰 재사용 가능 (데이터 정합성 오류)
```

## 서브 작업별 분석

### Sub-1: Order — publishCouponUsed() Outbox 전환

**파일**: `order/src/main/java/com/live_commerce/order/adapter/out/messaging/KafkaOrderEventPublisher.java`

**현재 코드**:
```java
@Override
public void publishCouponUsed(UUID couponId, UUID userId) {
    CouponUsedEvent event = new CouponUsedEvent(couponId, userId);
    kafkaTemplate.send("coupon-used", userId.toString(), event);  // ⚠️ 직접 호출
}
```

**변경 방향**:
- `OutboxEventHelper` 이미 있음 → 파일 1개만 수정
- `kafkaTemplate.send()` → `outboxEventHelper.saveEvent("ORDER", couponId, "COUPON_USED", "coupon-used", event)`
- `KafkaTemplate` 필드는 다른 메서드에서 사용하지 않으면 제거 가능

**복잡도**: 🟢 매우 낮음 (3줄 수정)

### Sub-2: Coupon — KafkaCouponEventPublisher Outbox 전환

**파일**: `coupon/src/main/java/com/live_commerce/coupon/adapter/out/messaging/KafkaCouponEventPublisher.java`

**현재 코드**:
```java
@Override
public void publishCouponUsedEvent(UUID couponId, UUID userId) {
    CouponUsedEvent event = new CouponUsedEvent(couponId, userId);
    kafkaTemplate.send("coupon-used", event);  // ⚠️ 직접 호출
}
```

**변경 방향**:
- Coupon 서비스에 Outbox 인프라 없음 → `common-lib` 의존성 추가 필요
- `OutboxEventHelper` 신규 생성 (`coupon.adapter.out.outbox` 패키지)
- `KafkaCouponEventPublisher` 수정

**복잡도**: 🟡 낮음 (Product 서비스 패턴 그대로 적용)

### Sub-3: Payment — PaymentEventProducer Outbox 전환

**파일**: `payment/src/main/java/com/live_commerce/payment/infrastructure/kafka/producer/PaymentEventProducer.java`

**현재 코드**:
```java
public void sendPaymentCompleted(PaymentCompletedEvent event) {
    send(COMPLETED_TOPIC, event.orderId().toString(), event);  // ⚠️ 직접 호출
}

public void sendPaymentFailed(PaymentFailedEvent event) {
    send(FAILED_TOPIC, event.orderId().toString(), event);  // ⚠️ 직접 호출
}
```

**변경 방향**:
- Payment 서비스에 Outbox 인프라 없음 → `common-lib` 의존성 추가 필요
- `OutboxEventHelper` 신규 생성 (`payment.infrastructure.outbox` 패키지)
- `PaymentEventProducer` → Outbox 기반으로 전환
- 호출자(`PaymentService`, `PaymentServiceV2`)에 `@Transactional` 컨텍스트 확인

**복잡도**: 🟡 낮음~중간

## 전체 파일 변경 목록

| 서비스 | 파일 | 변경 유형 |
|--------|------|----------|
| `order` | `KafkaOrderEventPublisher.java` | 수정 |
| `coupon` | `build.gradle` | 수정 (common-lib 추가) |
| `coupon` | `OutboxEventHelper.java` (신규) | 신규 |
| `coupon` | `KafkaCouponEventPublisher.java` | 수정 |
| `payment` | `build.gradle` | 수정 (common-lib 추가) |
| `payment` | `OutboxEventHelper.java` (신규) | 신규 |
| `payment` | `PaymentEventProducer.java` | 수정 |

## 구현 순서

```
Step 1. Order - publishCouponUsed() Outbox 전환 (가장 단순, 인프라 기존 보유)
Step 2. Coupon - common-lib 추가 + OutboxEventHelper 생성 + Publisher 수정
Step 3. Payment - common-lib 추가 + OutboxEventHelper 생성 + Producer 수정
         (Payment가 가장 중요하지만 구조 파악이 필요해 마지막)
Step 4. 빌드 검증: ./gradlew :order:compileJava :coupon:compileJava :payment:compileJava
```

## 완료 기준

- [ ] Order `publishCouponUsed()` — `kafkaTemplate.send()` 제거, Outbox 저장
- [ ] Coupon `build.gradle` — `common-lib` 추가
- [ ] Coupon `OutboxEventHelper` — 신규 생성
- [ ] Coupon `publishCouponUsedEvent()` — Outbox 저장
- [ ] Payment `build.gradle` — `common-lib` 추가
- [ ] Payment `OutboxEventHelper` — 신규 생성
- [ ] Payment `sendPaymentCompleted()`, `sendPaymentFailed()` — Outbox 저장
- [ ] `./gradlew :order:compileJava :coupon:compileJava :payment:compileJava` 성공
