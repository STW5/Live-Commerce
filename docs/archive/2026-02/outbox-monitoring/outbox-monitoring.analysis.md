# outbox-monitoring Analysis Report

> **Analysis Type**: Gap Analysis (Design vs Implementation)
>
> **Project**: Live Commerce Platform (MSA)
> **Analyst**: gap-detector
> **Date**: 2026-02-28
> **Design Doc**: [outbox-monitoring.design.md](../02-design/features/outbox-monitoring.design.md)

---

## 1. Analysis Overview

### 1.1 Analysis Purpose

outbox-monitoring 설계서(Section 7 체크리스트 기준 10개 항목)와 실제 구현 코드 간의 일치율을 검증한다.
Outbox 이벤트의 Prometheus 메트릭 노출, Slack 알림 연동, 관리 API, payment 서비스 통합 변경을 포함한다.

### 1.2 Analysis Scope

- **Design Document**: `docs/02-design/features/outbox-monitoring.design.md`
- **Implementation Paths**:
  - `common-lib/build.gradle`
  - `common-lib/src/main/java/com/live_commerce/common/notification/SlackNotificationService.java`
  - `common-lib/src/main/java/com/live_commerce/common/outbox/OutboxEventRepository.java`
  - `common-lib/src/main/java/com/live_commerce/common/outbox/OutboxEventPublisher.java`
  - `order/src/main/java/com/live_commerce/order/presentation/controller/OutboxAdminController.java`
  - `payment/build.gradle`
  - `payment/src/main/java/com/live_commerce/payment/infrastructure/kafka/consumer/PaymentDLQConsumer.java`
  - `payment/src/main/java/com/live_commerce/payment/infrastructure/notification/SlackNotificationService.java` (삭제 확인)

---

## 2. Gap Analysis (Design vs Implementation)

### 2.1 체크리스트 항목별 상세 분석

#### C-01: micrometer-core common-lib에 추가

| 항목 | 설계 | 구현 | 상태 |
|------|------|------|------|
| 의존성 | `implementation 'io.micrometer:micrometer-core'` | `common-lib/build.gradle` L38: `implementation 'io.micrometer:micrometer-core'` | **Complete** |

**판정**: 완전 일치.

---

#### C-02: SlackNotificationService common-lib common.notification 패키지에 존재

| 항목 | 설계 | 구현 | 상태 |
|------|------|------|------|
| 패키지 | `com.live_commerce.common.notification` | `common-lib/src/main/java/com/live_commerce/common/notification/SlackNotificationService.java` | **Complete** |
| @ConditionalOnProperty | `notification.slack.enabled`, `havingValue="true"`, `matchIfMissing=false` | L24: 동일 | **Complete** |
| sendCriticalAlert | `(String title, String message)` | L35: 동일 시그니처 | **Complete** |
| sendWarningAlert | `(String title, String message)` | L42: 동일 시그니처 | **Complete** |
| footer | `"Live Commerce MSA"` | L75: `"Live Commerce MSA"` | **Complete** |
| RestTemplate 주입 | 생성자 주입 | L27: `private final RestTemplate restTemplate` + `@RequiredArgsConstructor` | **Complete** |

**판정**: 완전 일치. escapeJson() 헬퍼 메서드가 추가되었으나 설계에 없는 안전한 개선(positive).

---

#### C-03: OutboxEventPublisher가 5개 메트릭을 등록

| 메트릭 | 설계 | 구현 위치 | 상태 |
|--------|------|-----------|------|
| `outbox.pending.count` (Gauge) | Section 3.4 @PostConstruct | L69-72: `Gauge.builder("outbox.pending.count", ...)` | **Complete** |
| `outbox.published.total` (Counter) | Section 3.4 | L53-55: `Counter.builder("outbox.published.total")` | **Complete** |
| `outbox.failed.total` (Counter) | Section 3.4 | L57-59: `Counter.builder("outbox.failed.total")` | **Complete** |
| `outbox.retry.total` (Counter) | Section 3.4 | L61-63: `Counter.builder("outbox.retry.total")` | **Complete** |
| `outbox.dead.total` (Counter) | Section 3.4 | L65-67: `Counter.builder("outbox.dead.total")` | **Complete** |
| MeterRegistry 생성자 주입 | 필수 (required=true) | L41: `private final MeterRegistry meterRegistry` | **Complete** |
| SlackNotificationService | `@Autowired(required=false)` | L43-44: `@Autowired(required = false)` | **Complete** |

**판정**: 완전 일치. 5개 메트릭 모두 설계서와 동일한 이름, 타입, 설명으로 등록됨.

---

#### C-04: retryCount >= 3 이벤트 발생 시 sendCriticalAlert() 호출

| 항목 | 설계 | 구현 | 상태 |
|------|------|------|------|
| sendDeadLetterAlert 메서드 | retryCount >= 3 -> sendCriticalAlert | L176-203: `sendDeadLetterAlert(OutboxEvent event)` -> `slackNotificationService.sendCriticalAlert(title, message)` | **Complete** |
| null 체크 | `if (slackNotificationService == null) return;` | L177: 동일 | **Complete** |
| 메시지 포맷 | aggregateType, aggregateId, topic, eventType, error | L181-196: 동일 필드 + retryCount 추가(positive) | **Complete** |
| deadCounter 증가 | `deadCounter.increment()` | L157: 호출 후 L158: `sendDeadLetterAlert(event)` | **Complete** |

**판정**: 완전 일치. 구현이 `retryCount` 필드와 수동 개입 안내 문구를 추가한 것은 positive gap.

---

#### C-05: PENDING > 30 시 sendWarningAlert() 호출

| 항목 | 설계 | 구현 | 상태 |
|------|------|------|------|
| WARNING 임계치 | `pendingEvents.size() > 30` | L36: `PENDING_WARNING_THRESHOLD = 30`, L92: `> PENDING_WARNING_THRESHOLD` | **Complete** |
| CRITICAL 임계치 | `pendingEvents.size() > 100` | L37: `PENDING_CRITICAL_THRESHOLD = 100`, L90: `> PENDING_CRITICAL_THRESHOLD` | **Complete** |
| alertSlack("WARNING") | sendWarningAlert 호출 | L214: `slackNotificationService.sendWarningAlert(...)` | **Complete** |
| alertSlack("CRITICAL") | sendCriticalAlert 호출 | L209: `slackNotificationService.sendCriticalAlert(...)` | **Complete** |

**판정**: 완전 일치. 매직 넘버를 상수로 추출한 것은 positive gap (설계보다 우수).

---

#### C-06: GET /api/v1/admin/outbox/stats 응답

| 항목 | 설계 | 구현 | 상태 |
|------|------|------|------|
| URL | `GET /api/v1/admin/outbox/stats` | L31: `@GetMapping("/stats")` | **Complete** |
| Response 타입 | `OutboxStatsResponse(pending, published, failed, dead)` | L71: `record OutboxStatsResponse(long pending, long published, long failed, long dead)` | **Complete** |
| pending 조회 | `countByStatus(PENDING)` | L33: 동일 | **Complete** |
| published 조회 | `countByStatus(PUBLISHED)` | L34: 동일 | **Complete** |
| failed 조회 | `countByStatus(FAILED)` | L35: 동일 | **Complete** |
| dead 조회 | `countDeadLetterEvents()` | L36: 동일 | **Complete** |

**판정**: 완전 일치.

---

#### C-07: POST /api/v1/admin/outbox/retry -> FAILED 이벤트 PENDING 복귀

| 항목 | 설계 | 구현 | 상태 |
|------|------|------|------|
| URL | `POST /api/v1/admin/outbox/retry` | L49: `@PostMapping("/retry")` | **Complete** |
| Body | `RetryRequest(List<UUID> ids)` 또는 비어있으면 전체 FAILED | L49: `@RequestBody(required = false) RetryRequest req`, L52: null/empty 체크 -> 전체 Dead Letter | **Complete** |
| 복귀 로직 | `resetForRetry()` 호출 | L63: `event.resetForRetry()` -> `save(event)` | **Complete** |
| GET /failed | Dead Letter 이벤트 목록 | L42: `@GetMapping("/failed")` -> `findDeadLetterEvents()` | **Complete** |

**판정**: 완전 일치. `@Operation` Swagger 어노테이션 추가는 positive gap.

---

#### C-08: payment 서비스 로컬 SlackNotificationService 삭제

| 항목 | 설계 | 구현 | 상태 |
|------|------|------|------|
| 파일 삭제 | `payment/infrastructure/notification/SlackNotificationService.java` 삭제 | Glob 결과: No files found (파일 없음) | **Complete** |

**판정**: 완전 일치. 파일이 삭제되었음을 확인.

---

#### C-09: payment PaymentDLQConsumer가 common.notification.SlackNotificationService import 사용

| 항목 | 설계 | 구현 | 상태 |
|------|------|------|------|
| import 변경 | `import com.live_commerce.common.notification.SlackNotificationService` | L12: `import com.live_commerce.common.notification.SlackNotificationService;` | **Complete** |
| common-lib 의존성 | `payment/build.gradle`에 `project(':common-lib')` | L100: `implementation project(':common-lib')` + L99 주석: `// common-lib (Outbox Pattern, SlackNotificationService)` | **Complete** |

**판정**: 완전 일치.

---

#### C-10: findRetryableFailedEvents()의 retryCount < 3 (기존 < 5 수정)

| 항목 | 설계 | 구현 | 상태 |
|------|------|------|------|
| 쿼리 수정 | `retryCount < 5` -> `retryCount < 3` | L23: `o.retryCount < 3` | **Complete** |
| canRetry() 일치 | retryCount < 3 기준 통일 | Repository L23 = `< 3`, OutboxEvent.canRetry() = `< 3` (일치) | **Complete** |

**판정**: 완전 일치.

---

### 2.2 추가 구현 항목 (Design X, Implementation O)

| # | 항목 | 구현 위치 | 설명 | 영향도 |
|---|------|-----------|------|--------|
| P-01 | escapeJson() 헬퍼 | SlackNotificationService L88-95 | JSON 특수문자 이스케이프 처리 | Low (안전성 개선) |
| P-02 | 상수 추출 | OutboxEventPublisher L36-37 | PENDING_WARNING_THRESHOLD, PENDING_CRITICAL_THRESHOLD 상수화 | Low (코드 품질 개선) |
| P-03 | sendDeadLetterAlert에 retryCount 포함 | OutboxEventPublisher L191 | 설계에 없던 retryCount 필드를 알림 메시지에 추가 | Low (운영 편의) |
| P-04 | 수동 개입 안내 문구 | OutboxEventPublisher L189 | Dead Letter 알림에 `POST /api/v1/admin/outbox/retry` 안내 포함 | Low (운영 편의) |
| P-05 | try-catch 보호 | OutboxEventPublisher L178,207 | sendDeadLetterAlert, alertSlack 모두 try-catch 래핑 | Low (안정성 개선) |
| P-06 | @Operation Swagger 어노테이션 | OutboxAdminController L30,40,46 | API 문서화 어노테이션 추가 | Low (문서 개선) |

---

### 2.3 OutboxEventRepository 메서드 추가 확인

| 메서드 | 설계 | 구현 | 상태 |
|--------|------|------|------|
| `countByStatus(OutboxStatus)` | Section 3.3 | L41: `long countByStatus(OutboxStatus status)` | **Complete** |
| `countDeadLetterEvents()` | Section 3.3 @Query | L35-36: `@Query("SELECT COUNT(o) FROM OutboxEvent o WHERE o.status = 'FAILED' AND o.retryCount >= 3")` | **Complete** |
| `findDeadLetterEvents()` | Section 3.3 @Query | L29-30: `@Query("SELECT o FROM OutboxEvent o WHERE o.status = 'FAILED' AND o.retryCount >= 3 ORDER BY o.createdAt DESC")` | **Complete** |
| `findRetryableFailedEvents()` 수정 | `retryCount < 5` -> `retryCount < 3` | L23: `o.retryCount < 3` | **Complete** |

---

### 2.4 Match Rate Summary

```
+---------------------------------------------+
|  Overall Match Rate: 100.0%                  |
+---------------------------------------------+
|  Complete:           10 / 10 items (100%)    |
|  Positive (added):    6 items               |
|  Changed:             0 items               |
|  Missing:             0 items               |
+---------------------------------------------+
```

---

## 3. Overall Scores

| Category | Score | Status |
|----------|:-----:|:------:|
| Design Match | 100% | PASS |
| Architecture Compliance | 98% | PASS |
| Convention Compliance | 98% | PASS |
| **Overall** | **100%** | **PASS** |

---

## 4. Architecture Compliance (98%)

| 검증 항목 | 상태 | 비고 |
|-----------|------|------|
| SlackNotificationService -> common-lib 이동 | PASS | common.notification 패키지 |
| OutboxEventPublisher MeterRegistry 생성자 주입 (필수) | PASS | ADR-2 준수 |
| SlackNotificationService @Autowired(required=false) (선택) | PASS | ADR-3 준수 |
| OutboxAdminController order 서비스에만 배치 | PASS | ADR-4 준수 |
| payment 로컬 SlackNotificationService 삭제 | PASS | DRY 원칙 달성 |
| PaymentDLQConsumer common-lib import 사용 | PASS | 교차 모듈 의존성 정리 |
| @ConditionalOnProperty 선택적 활성화 | PASS | Slack 미설정 시 정상 기동 보장 |

**-2% 차감 사유**: OutboxAdminController가 OutboxEventRepository(common-lib JPA 엔티티)를 직접 주입받아 사용. Application 서비스 계층 없이 Presentation에서 직접 Repository 호출은 Hexagonal 원칙상 Port/UseCase를 거치는 것이 이상적이나, Admin API 특성상 허용 가능한 수준.

---

## 5. Convention Compliance (98%)

| 검증 항목 | 상태 | 비고 |
|-----------|------|------|
| 클래스명 PascalCase | PASS | SlackNotificationService, OutboxAdminController, OutboxEventPublisher |
| 메서드명 camelCase | PASS | sendCriticalAlert, findDeadLetterEvents, publishPendingEvents |
| 상수 UPPER_SNAKE_CASE | PASS | PENDING_WARNING_THRESHOLD, PENDING_CRITICAL_THRESHOLD |
| record 네이밍 | PASS | OutboxStatsResponse, RetryRequest |
| @Slf4j 사용 | PASS | 모든 신규/변경 클래스 |
| @RequiredArgsConstructor DI | PASS | @Autowired 미사용 (SlackNotificationService 선택적 제외) |
| Javadoc/주석 | PASS | 주요 메서드에 설명 주석 포함 |

**-2% 차감 사유**: OutboxAdminController 내부에 record 정의(OutboxStatsResponse, RetryRequest)가 inner class로 존재. 별도 DTO 파일로 분리하는 것이 프로젝트 컨벤션에 더 부합하나, Admin API의 간결성 목적상 허용 가능.

---

## 6. Recommended Actions

### 6.1 No Immediate Actions Required

모든 10개 체크리스트 항목이 완전 일치(100%)하므로 즉각 조치가 필요한 Gap은 없다.

### 6.2 Optional Improvements (Low Priority)

| # | 항목 | 설명 | 영향도 |
|---|------|------|--------|
| 1 | OutboxAdminController UseCase 분리 | Repository 직접 호출 -> OutboxAdminUseCase + Service 분리 | Low |
| 2 | DTO 파일 분리 | OutboxStatsResponse, RetryRequest를 별도 파일로 분리 | Low |
| 3 | 빌드 검증 | `./gradlew :common-lib:compileJava :order:compileJava :payment:compileJava` 실행 확인 | Medium |

---

## 7. Design Document Updates Needed

설계서 업데이트 필요 항목 없음. 구현이 설계를 충실히 따르면서 positive gap만 존재.

Positive gap 반영을 원할 경우:
- [ ] sendDeadLetterAlert 메시지에 retryCount 필드 및 수동 개입 안내 문구 추가 반영
- [ ] try-catch 보호 패턴 반영
- [ ] 상수 추출 패턴 반영

---

## 8. Conclusion

outbox-monitoring 구현은 설계서의 10개 체크리스트 항목을 **100% 충족**한다.
6개의 positive gap(설계에 없으나 구현에서 추가된 개선 사항)이 존재하며, 모두 코드 품질 및 운영 편의성 향상에 기여한다.
설계서와 구현 간의 불일치(missing/changed)는 0건이다.

Match Rate 100%로 90% 임계치를 초과하므로, Check 단계를 완료하고 Report 단계로 진행할 수 있다.

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0 | 2026-02-28 | Initial gap analysis (10 checklist items) | gap-detector |
