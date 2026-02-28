# Plan: outbox-monitoring

> Outbox 이벤트 처리 실패 감지 및 운영 알림 체계 구축

**작성일**: 2026-02-27
**우선순위**: High
**예상 범위**: Medium (common-lib + order + payment 서비스)

---

## 1. 배경 및 목적

### 현재 문제점

`OutboxEventPublisher`(common-lib)는 5초마다 PENDING 이벤트를 Kafka로 발행하고
실패 시 최대 3회 재시도하지만, **실패를 외부에서 감지할 방법이 없음**:

- Prometheus 메트릭 없음 → Grafana 대시보드에서 Outbox 상태 불가시
- retryCount >= 3 (더 이상 재시도 불가) 이벤트 발생 시 운영팀 알림 없음
- PENDING 이벤트가 비정상적으로 쌓여도 감지 불가
- 현재 `SlackNotificationService`는 payment 서비스에만 존재

### 목표

1. **가시성 (Visibility)**: Prometheus 메트릭으로 Outbox 상태를 실시간 수치화
2. **알림 (Alerting)**: 임계치 초과 시 Slack 자동 알림
3. **관리 API**: FAILED 이벤트 목록 조회 및 수동 재시도 엔드포인트

---

## 2. 현재 구현 상태 (As-Is)

### common-lib
```
common-lib/src/main/java/com/live_commerce/common/outbox/
├── OutboxEvent.java          ← PENDING/PUBLISHED/FAILED, retryCount 최대 3
├── OutboxEventPublisher.java ← 5초 폴링, 1분 재시도, 매일 정리 (메트릭 없음)
├── OutboxEventRepository.java
└── OutboxStatus.java
```

### payment 서비스
```
payment/.../infrastructure/notification/SlackNotificationService.java
  - @ConditionalOnProperty("notification.slack.enabled")
  - sendCriticalAlert(), sendWarningAlert() 구현됨
  - PaymentDLQConsumer에서 DLQ 알림에 활용 중
```

### order 서비스
```
order/.../adapter/out/messaging/KafkaOrderEventPublisher.java
  - inventory-decrease, inventory-rollback, order-failed → Outbox 사용
  - Slack 알림 없음
```

---

## 3. 요구사항

### FR-01: Prometheus 메트릭 (common-lib)

`OutboxEventPublisher`에 `MeterRegistry` 주입 후 메트릭 추가:

| 메트릭 이름 | 타입 | 설명 |
|------------|------|------|
| `outbox.pending.count` | Gauge | 현재 PENDING 이벤트 수 (topic별 tag) |
| `outbox.published.total` | Counter | 누적 발행 성공 수 |
| `outbox.failed.total` | Counter | 누적 발행 실패 수 |
| `outbox.retry.total` | Counter | 누적 재시도 수 |
| `outbox.dead.total` | Counter | retryCount >= 3, 더 이상 재시도 불가 이벤트 수 |

### FR-02: Slack 알림 (common-lib)

`SlackNotificationService`를 **common-lib으로 이동** (현재 payment 전용 → 공통화):

**알림 트리거:**

| 조건 | 알림 수준 | 메시지 |
|------|----------|--------|
| `outbox.dead.total` 증가 | CRITICAL (빨간색) | 수동 개입 필요, topic/aggregateId 포함 |
| PENDING 이벤트 30건 초과 | WARNING (주황색) | Kafka 연결 또는 처리 지연 가능성 |

### FR-03: FAILED 이벤트 관리 API

`OutboxAdminController` 추가 (order 서비스 `SagaAdminController` 패턴 참고):

```
GET  /actuator/outbox/failed    → FAILED 이벤트 목록 (retryCount별 집계)
POST /actuator/outbox/retry     → FAILED 이벤트 수동 PENDING 복귀
GET  /actuator/outbox/stats     → PENDING/PUBLISHED/FAILED 건수 요약
```

### NFR-01: 비기능 요구사항
- Slack URL 미설정 시 알림 발송 건너뜀 (graceful degradation)
- 메트릭 수집 실패가 Outbox 발행 로직을 막지 않음 (try-catch 보호)
- 관리 API는 `/actuator/**` 경로로 Gateway 보안 예외 처리 (이미 적용)

---

## 4. 구현 범위

### Phase 1: common-lib 메트릭 + 알림 (핵심)
- `OutboxEventPublisher`에 `MeterRegistry` 주입 → 5개 메트릭 추가
- `SlackNotificationService` common-lib으로 이동 (`@ConditionalOnProperty` 유지)
- Dead 이벤트 발생 시 Slack CRITICAL 알림 트리거

### Phase 2: 관리 API (order 서비스)
- `OutboxAdminController` 추가 (GET stats, GET failed, POST retry)
- `OutboxEventRepository`에 `countByStatus()` 쿼리 추가

### Phase 3: payment 서비스 SlackNotificationService 교체
- 기존 payment 전용 SlackNotificationService → common-lib 버전으로 교체
- `PaymentDLQConsumer`의 Slack 알림 경로 유지 (API 동일)

---

## 5. 제외 범위

- Grafana 대시보드 JSON 파일 생성 (별도 운영 작업)
- PagerDuty / Email 알림 (Slack만 구현)
- 메트릭 히스토그램/percentile (Counter/Gauge만 구현)

---

## 6. 의존성

| 서비스 | 추가 의존성 |
|--------|------------|
| common-lib | `micrometer-core` (이미 Spring Boot Actuator에 포함) |
| common-lib | `spring-web` (RestTemplate for Slack) |
| order | common-lib 버전업 |
| payment | common-lib SlackNotificationService 교체 |

---

## 7. 완료 기준

- [ ] `outbox.pending.count`, `outbox.failed.total` 등 5개 메트릭이 `/actuator/prometheus`에 노출
- [ ] retryCount >= 3 이벤트 발생 시 Slack CRITICAL 알림 발송
- [ ] `GET /actuator/outbox/stats` → `{"pending":0,"published":10,"failed":0}` 응답
- [ ] Slack URL 미설정 상태에서도 서비스 정상 기동 확인
