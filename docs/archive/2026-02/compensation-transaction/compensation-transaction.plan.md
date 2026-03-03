# Plan: 보상 트랜잭션 (Compensation Transaction) 안정화 구현

**Feature:** compensation-transaction
**작성일:** 2026-02-13
**담당자:** -
**PDCA 단계:** Plan

---

## 1. 배경 및 목적

### 1-1. 현재 상태 (As-Is)

Live Commerce 플랫폼은 **Choreography 기반 Saga 패턴**으로 분산 트랜잭션을 처리하고 있습니다.

**기본 보상 트랜잭션 플로우 (구현 완료):**
```
결제 실패
  → payment-failed (Kafka)
  → Order Service: 주문 상태 FAILED
  → inventory-rollback (Kafka) → Product Service: 재고 복구
  → order-failed (Kafka)
      → Payment Service: KakaoPay 환불
      → Coupon Service: 쿠폰 복구
  → DLQ: 최종 실패 시 Slack 알림
```

**현재 구현된 내용:**
- ✅ payment-failed → 재고 롤백 + 주문 취소
- ✅ order-failed → 환불 + 쿠폰 복구
- ✅ 멱등성 체크 (Order, Payment 서비스)
- ✅ Exponential Backoff 재시도 (1s → 2s → 4s, 최대 3회)
- ✅ DLQ 처리 및 Slack 알림
- ✅ common-lib 모듈 스켈레톤 (OutboxEvent, SagaState 정의)
- ✅ event-schema 모듈 스켈레톤 (이벤트 DTO 정의)

### 1-2. 문제점 (Pain Points)

| # | 문제 | 리스크 | 영향도 |
|---|------|--------|--------|
| P1 | **이벤트 유실 위험**: DB 커밋 성공 후 Kafka 발행 실패 시 이벤트 영구 소실 | 주문 데이터와 재고/결제 불일치 | 🔴 Critical |
| P2 | **Saga 상태 추적 불가**: 어느 단계에서 실패했는지 중앙 추적 불가 | 장애 원인 파악 어려움, 수동 복구 비용 증가 | 🟡 High |
| P3 | **이벤트 스키마 중복**: PaymentCompletedEvent 등이 여러 서비스에 복제 존재 | 버전 불일치 시 런타임 역직렬화 오류 | 🟡 High |
| P4 | **Coupon 복구 DLQ 미구현**: coupon 서비스에 DLQ/재시도 없음 | 쿠폰 복구 실패 시 미탐지 | 🟠 Medium |

### 1-3. 목표 (To-Be)

```
보상 트랜잭션 신뢰성 지표:
- 이벤트 유실 발생율: 현재 미측정 → 목표 0% (Outbox Pattern)
- 장애 감지 시간: 현재 수동 → 목표 즉시 (Saga State + 모니터링)
- 보상 트랜잭션 성공률: 현재 미측정 → 목표 99.9%+
```

---

## 2. 구현 범위 (Scope)

### In-Scope (이번 구현)

| Phase | 항목 | 서비스 |
|-------|------|--------|
| Phase 1 | event-schema 모듈 서비스 통합 | order, payment, product, coupon |
| Phase 2 | Outbox Pattern 구현 및 Order 서비스 적용 | common-lib, order |
| Phase 3 | Saga State 추적 구현 | common-lib, order |
| Phase 4 | 보상 트랜잭션 전체 흐름 검증 | 전체 |

### Out-of-Scope (제외)

- Saga Orchestrator 패턴으로의 전환 (Choreography 유지)
- common-lib의 SecurityConfig 통합 (별도 이슈)
- payment, product 서비스의 Outbox Pattern 적용 (Phase 2 이후)

---

## 3. 기능 요구사항 (Functional Requirements)

### FR-01: Outbox Pattern (이벤트 신뢰성)

```
주문 생성/결제 실패 처리 시:
1. DB 트랜잭션 내에서 OutboxEvent 테이블에 이벤트 저장 (원자적)
2. OutboxEventPublisher가 주기적으로(5초) PENDING 이벤트 조회 및 Kafka 발행
3. 발행 성공 시 OutboxEvent 상태를 SENT로 업데이트
4. 발행 실패 시 재시도 카운트 증가, 최대 3회 후 FAILED 상태
5. FAILED 이벤트는 알림 발송 (Slack)
```

**Outbox 테이블 (orders 스키마):**
```sql
outbox_events (
  id UUID PK,
  aggregate_type VARCHAR,  -- 'ORDER'
  aggregate_id UUID,       -- orderId
  event_type VARCHAR,      -- 'inventory-decrease', 'order-failed'
  payload TEXT,            -- JSON
  status ENUM(PENDING, SENT, FAILED),
  retry_count INT DEFAULT 0,
  created_at TIMESTAMP,
  sent_at TIMESTAMP
)
```

### FR-02: Saga State 추적

```
분산 트랜잭션 각 단계 상태 기록:
1. Order 생성 → SagaState 생성 (STARTED)
2. 재고 감소 요청 → INVENTORY_DECREASING
3. 재고 감소 완료 → INVENTORY_DECREASED
4. 결제 준비 → PAYMENT_READY
5. 결제 완료 → COMPLETED
6. 실패 → FAILED (실패 단계 기록)
7. 보상 완료 → COMPENSATED
```

**SagaState 테이블 (orders 스키마):**
```sql
saga_states (
  id UUID PK,
  order_id UUID UNIQUE,
  current_step VARCHAR,    -- SagaStatus enum
  status ENUM(STARTED, COMPLETED, FAILED, COMPENSATED),
  last_event VARCHAR,
  failure_reason TEXT,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
)
```

### FR-03: event-schema 모듈 통합

```
기존 서비스 내 이벤트 DTO를 event-schema 모듈로 일원화:
- PaymentCompletedEvent: order, payment 서비스에서 event-schema 의존
- PaymentFailedEvent: order, payment 서비스에서 event-schema 의존
- OrderFailedEvent: order, payment, coupon 서비스에서 event-schema 의존
- InventoryRollbackEvent: order, product 서비스에서 event-schema 의존
- InventoryDecreasedEvent: order, product 서비스에서 event-schema 의존
```

### FR-04: Coupon DLQ 처리

```
쿠폰 복구 실패 시:
1. 최대 3회 재시도 (Exponential Backoff)
2. 실패 시 order-failed.DLQ 토픽으로 전송
3. DLQ Consumer가 Slack 알림 발송
```

---

## 4. 비기능 요구사항 (Non-Functional Requirements)

| 항목 | 요구사항 |
|------|----------|
| **신뢰성** | 이벤트 At-Least-Once 보장 (Outbox + 수동 ACK) |
| **멱등성** | 동일 이벤트 중복 처리 방지 (orderId 기반 상태 체크) |
| **관측 가능성** | SagaState 조회 API로 현재 단계 모니터링 가능 |
| **성능** | Outbox 폴링으로 인한 지연 최대 5초 이내 |
| **테스트** | 각 보상 시나리오에 대한 단위 테스트 작성 |

---

## 5. 기술 결정 (Technical Decisions)

### 5-1. Choreography Saga 유지 (Orchestrator 미적용)

**이유:**
- 현재 Choreography 방식이 이미 동작 중이며 리스크 최소화
- Orchestrator 도입 시 order 서비스에 모든 흐름 집중 → 단일 장애점
- 추후 복잡도 증가 시 Orchestrator 전환 검토

### 5-2. Outbox Pattern 적용 범위 (Order 서비스 우선)

**이유:**
- Order 서비스가 Saga 플로우의 시작점이자 보상 트랜잭션 발행 주체
- 가장 많은 이벤트를 발행 (inventory-decrease, inventory-rollback, order-failed)
- Payment, Product 서비스는 Phase 2 이후 적용

### 5-3. Outbox Polling vs CDC (Change Data Capture)

**결정: Polling 방식 (Scheduled)**
- CDC(Debezium)는 별도 인프라 구성 필요 → 현 단계에서 오버엔지니어링
- 5초 폴링은 성능상 충분 (주문 처리는 실시간성 필수 아님)
- 이후 트래픽 증가 시 CDC 전환 검토

---

## 6. 구현 순서 (Implementation Order)

```
Step 1: event-schema 모듈 서비스 통합 (1~2일)
  ├── event-schema build.gradle 의존성 확인
  ├── order 서비스: 기존 이벤트 DTO → event-schema로 교체
  ├── payment 서비스: PaymentCompletedEvent, PaymentFailedEvent 교체
  ├── product 서비스: InventoryRollbackEvent, InventoryDecreasedEvent 교체
  └── coupon 서비스: OrderFailedEvent 교체

Step 2: Outbox Pattern 구현 (2~3일)
  ├── common-lib: OutboxEvent JPA Entity 완성 (orders 스키마)
  ├── common-lib: OutboxEventRepository 구현
  ├── common-lib: OutboxEventPublisher @Scheduled 폴링 구현
  ├── order 서비스: build.gradle에 common-lib 의존성 추가
  ├── order 서비스: inventory-decrease 이벤트 Outbox로 전환
  ├── order 서비스: inventory-rollback 이벤트 Outbox로 전환
  └── order 서비스: order-failed 이벤트 Outbox로 전환

Step 3: Saga State 추적 (1~2일)
  ├── common-lib: SagaState JPA Entity 완성
  ├── common-lib: SagaStateRepository 구현
  ├── order 서비스: OrderCreateService에 SagaState 생성 로직 추가
  ├── order 서비스: 각 Kafka Consumer에 SagaState 업데이트 로직 추가
  └── order 서비스: SagaState 조회 API 엔드포인트 추가 (관리자용)

Step 4: Coupon DLQ 처리 + 전체 검증 (1일)
  ├── coupon 서비스: KafkaConfig Retry + DLQ 설정 추가
  ├── coupon 서비스: DLQ Consumer 구현
  └── 전체 보상 트랜잭션 시나리오 통합 테스트
```

---

## 7. 위험 요소 (Risks)

| 위험 | 가능성 | 영향 | 대응 |
|------|--------|------|------|
| Outbox 폴링 중 DB 부하 | 중 | 중 | 인덱스 최적화 (status, created_at) |
| event-schema 마이그레이션 중 빌드 오류 | 고 | 중 | 서비스별 단계적 적용, 기존 DTO 유지 후 deprecation |
| SagaState 저장 실패 | 저 | 저 | 보조 기능으로 처리 (메인 플로우에 영향 없음) |
| OutboxEvent 중복 발행 | 중 | 중 | Consumer 멱등성 체크로 처리 (이미 구현) |

---

## 8. 성공 기준 (Acceptance Criteria)

- [ ] event-schema 의존성 교체 후 전 서비스 빌드 성공
- [ ] Order 서비스의 모든 Kafka 이벤트 발행이 Outbox 테이블 경유
- [ ] 결제 실패 시 SagaState가 FAILED + 실패 단계 기록됨
- [ ] 전체 보상 트랜잭션 완료 시 SagaState가 COMPENSATED로 업데이트됨
- [ ] Outbox 폴링 실패 시 Slack 알림 발송
- [ ] 단위 테스트 통과율 100%

---

## 9. 참고 문서

- `SAGA_OUTBOX_IMPLEMENTATION_GUIDE.md` - 기술 가이드 (프로젝트 루트)
- `common-lib/` - Outbox/Saga 스켈레톤 코드
- `event-schema/` - 이벤트 DTO 스켈레톤 코드
- CLAUDE.md - 아키텍처 및 서비스 구조 설명

---

*Plan 문서 완료. 다음 단계: `/pdca design compensation-transaction`*
