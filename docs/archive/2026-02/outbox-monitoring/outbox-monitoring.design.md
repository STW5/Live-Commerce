# Design: outbox-monitoring

> Outbox 이벤트 처리 실패 감지 및 운영 알림 체계 구축

**작성일**: 2026-02-27
**참조 Plan**: `docs/01-plan/features/outbox-monitoring.plan.md`
**영향 모듈**: `common-lib`, `order`, `payment`

---

## 1. As-Is 현황

### 1.1 문제 상황

```
OutboxEventPublisher (5초 폴링)
  ├── PENDING 이벤트 발행
  │     성공 → PUBLISHED
  │     실패 → FAILED (retryCount++)
  │
  └── 1분마다 재시도: FAILED → PENDING (retryCount < 3)
        retryCount >= 3 → 영구 FAILED ← ⚠️ 아무도 모름
```

- **가시성 없음**: Prometheus에 아무 메트릭도 없음
- **Dead Letter 무음**: retryCount >= 3 이벤트 발생해도 알림 없음
- **SlackNotificationService**: payment 서비스에만 존재, 다른 서비스는 미사용
- **관리 API 없음**: FAILED 이벤트 현황 조회 불가

### 1.2 현재 파일 구조

```
common-lib/
  outbox/
    OutboxEvent.java          ← PENDING/PUBLISHED/FAILED, canRetry() retryCount < 3
    OutboxEventPublisher.java ← @Scheduled 5초/1분/매일 (MeterRegistry 없음)
    OutboxEventRepository.java ← findByStatus, findRetryableFailedEvents (retryCount < 5)
    OutboxStatus.java

payment/infrastructure/notification/
    SlackNotificationService.java ← sendCriticalAlert, sendWarningAlert (payment 전용)

order/presentation/controller/
    SagaAdminController.java  ← 관리 API 패턴 참고
```

### 1.3 불일치 발견

`OutboxEvent.canRetry()` → `retryCount < 3`
`OutboxEventRepository.findRetryableFailedEvents()` → `retryCount < 5`
→ 실제로는 retryCount >= 3이면 더 이상 PENDING 복귀 안 됨 (canRetry() 로직 우선)

---

## 2. To-Be 설계

### 2.1 전체 아키텍처

```
[OutboxEventPublisher] ──────────────────────────────────────────────────────
    │ publishPendingEvents() (5초)              │ retryFailedEvents() (1분)
    │                                           │
    ├─ 성공 → PUBLISHED                         ├─ retryCount < 3 → PENDING
    │   └─ Counter: outbox.published.total       │
    │                                           └─ retryCount >= 3 → Dead Letter
    └─ 실패 → FAILED                                └─ Counter: outbox.dead.total
        └─ Counter: outbox.failed.total               └─ Slack CRITICAL 알림
                                                            ↓
[/actuator/prometheus]                          [SlackNotificationService (common-lib)]
    outbox.pending.count (Gauge)                    sendCriticalAlert()
    outbox.published.total (Counter)                sendWarningAlert()
    outbox.failed.total (Counter)
    outbox.retry.total (Counter)
    outbox.dead.total (Counter)

[OutboxAdminController] (order 서비스)
    GET  /api/v1/admin/outbox/stats   → 상태별 카운트
    GET  /api/v1/admin/outbox/failed  → FAILED 이벤트 목록
    POST /api/v1/admin/outbox/retry   → 수동 PENDING 복귀
```

### 2.2 PENDING 임계치 알림

```
publishPendingEvents() 실행 시:
  pendingEvents.size() > 30 → Slack WARNING ("Outbox 처리 지연 가능성")
  pendingEvents.size() > 100 → Slack CRITICAL ("즉시 확인 필요")
```

---

## 3. 컴포넌트별 변경 명세

### 3.1 common-lib/build.gradle

**추가 의존성:**
```groovy
// Prometheus 메트릭
implementation 'io.micrometer:micrometer-core'
```

> `spring-boot-starter-actuator`는 각 서비스에 이미 있으므로 common-lib에는 core만 추가

---

### 3.2 common-lib: SlackNotificationService (신규)

**경로**: `common-lib/src/main/java/com/live_commerce/common/notification/SlackNotificationService.java`

```java
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackNotificationService {
    private final RestTemplate restTemplate;

    @Value("${notification.slack.webhook-url:}")
    private String slackWebhookUrl;

    public void sendCriticalAlert(String title, String message) { ... }
    public void sendWarningAlert(String title, String message)  { ... }

    // footer: "Live Commerce MSA" (기존 "Live Commerce Payment Service" 에서 변경)
}
```

**이동 이유**: common-lib에 `spring-boot-starter-web`이 이미 있어 RestTemplate 사용 가능.
모든 서비스가 Outbox를 쓰므로 공통 위치가 맞음.

---

### 3.3 common-lib: OutboxEventRepository (메서드 추가)

```java
// 상태별 카운트 (stats API용)
long countByStatus(OutboxStatus status);

// Dead Letter 이벤트 수 (retryCount >= 3 & FAILED)
@Query("SELECT COUNT(o) FROM OutboxEvent o WHERE o.status = 'FAILED' AND o.retryCount >= 3")
long countDeadLetterEvents();

// Dead Letter 이벤트 목록 (관리 API용)
@Query("SELECT o FROM OutboxEvent o WHERE o.status = 'FAILED' AND o.retryCount >= 3 ORDER BY o.createdAt DESC")
List<OutboxEvent> findDeadLetterEvents();

// ※ findRetryableFailedEvents()의 retryCount < 5 → retryCount < 3 으로 수정 (canRetry() 일치)
```

---

### 3.4 common-lib: OutboxEventPublisher (핵심 변경)

**MeterRegistry + SlackNotificationService 주입:**

```java
@RequiredArgsConstructor
public class OutboxEventPublisher {
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;                    // 신규

    @Autowired(required = false)
    private SlackNotificationService slackNotificationService;    // 신규 (선택적)

    // 카운터 초기화 (생성자 또는 @PostConstruct)
    private Counter publishedCounter;
    private Counter failedCounter;
    private Counter retryCounter;
    private Counter deadCounter;
}
```

**메트릭 등록 (`@PostConstruct`):**

```java
@PostConstruct
void initMetrics() {
    publishedCounter = Counter.builder("outbox.published.total")
        .description("Outbox 발행 성공 누적").register(meterRegistry);
    failedCounter = Counter.builder("outbox.failed.total")
        .description("Outbox 발행 실패 누적").register(meterRegistry);
    retryCounter = Counter.builder("outbox.retry.total")
        .description("Outbox 재시도 누적").register(meterRegistry);
    deadCounter = Counter.builder("outbox.dead.total")
        .description("Outbox Dead Letter 누적").register(meterRegistry);
    // Gauge: 매 측정 시 DB 조회
    Gauge.builder("outbox.pending.count", outboxEventRepository,
            repo -> repo.countByStatus(OutboxStatus.PENDING))
        .description("현재 PENDING 이벤트 수")
        .register(meterRegistry);
}
```

**publishPendingEvents() 변경:**

```java
@Scheduled(fixedDelay = 5000)
public void publishPendingEvents() {
    List<OutboxEvent> pendingEvents = ...;
    if (pendingEvents.isEmpty()) return;

    // 임계치 알림
    if (pendingEvents.size() > 100) {
        alertSlack("CRITICAL", pendingEvents.size());
    } else if (pendingEvents.size() > 30) {
        alertSlack("WARNING", pendingEvents.size());
    }

    for (OutboxEvent event : pendingEvents) {
        publishEvent(event);
    }
}
```

**publishEvent() 변경:**

```java
private void publishEvent(OutboxEvent event) {
    try {
        kafkaTemplate.send(...).get(5, TimeUnit.SECONDS);
        updateEventStatus(event.getId(), true, null);
        publishedCounter.increment();                            // 신규
    } catch (Exception e) {
        updateEventStatus(event.getId(), false, e.getMessage());
        failedCounter.increment();                               // 신규
    }
}
```

**retryFailedEvents() 변경:**

```java
@Scheduled(fixedDelay = 60000)
public void retryFailedEvents() {
    List<OutboxEvent> failedEvents = outboxEventRepository.findRetryableFailedEvents();

    for (OutboxEvent event : failedEvents) {
        if (event.canRetry()) {
            event.resetForRetry();
            retryCounter.increment();                            // 신규
        } else {
            // Dead Letter: 더 이상 재시도 불가
            deadCounter.increment();                             // 신규
            sendDeadLetterAlert(event);                          // 신규
        }
    }
}

private void sendDeadLetterAlert(OutboxEvent event) {
    if (slackNotificationService == null) return;
    String title = "🚨 [CRITICAL] Outbox Dead Letter 이벤트 발생";
    String msg = String.format(
        "aggregateType: %s\naggregateId: %s\ntopic: %s\neventType: %s\nerror: %s",
        event.getAggregateType(), event.getAggregateId(),
        event.getTopic(), event.getEventType(), event.getErrorMessage()
    );
    slackNotificationService.sendCriticalAlert(title, msg);
}

private void alertSlack(String level, int count) {
    if (slackNotificationService == null) return;
    if ("CRITICAL".equals(level)) {
        slackNotificationService.sendCriticalAlert(
            "🚨 Outbox 처리 지연 심각",
            "PENDING 이벤트 " + count + "건 쌓임. Kafka 연결 확인 필요."
        );
    } else {
        slackNotificationService.sendWarningAlert(
            "⚠️ Outbox 처리 지연 감지",
            "PENDING 이벤트 " + count + "건. 모니터링 권장."
        );
    }
}
```

---

### 3.5 order 서비스: OutboxAdminController (신규)

**경로**: `order/src/main/java/com/live_commerce/order/presentation/controller/OutboxAdminController.java`

```java
@Tag(name = "Outbox Admin", description = "Outbox 이벤트 관리 (관리자 전용)")
@RestController
@RequestMapping("/api/v1/admin/outbox")
@RequiredArgsConstructor
public class OutboxAdminController {

    private final OutboxEventRepository outboxEventRepository;

    // GET /api/v1/admin/outbox/stats
    // Response: {"pending":0, "published":10, "failed":2, "dead":1}
    @GetMapping("/stats")
    public ResponseEntity<OutboxStatsResponse> getStats() { ... }

    // GET /api/v1/admin/outbox/failed
    // Response: List<OutboxEvent> (FAILED 상태 전체)
    @GetMapping("/failed")
    public ResponseEntity<List<OutboxEvent>> getFailedEvents() { ... }

    // POST /api/v1/admin/outbox/retry
    // Body: {"ids": ["uuid1", "uuid2"]} or 비어있으면 전체 FAILED
    @PostMapping("/retry")
    public ResponseEntity<String> retryFailedEvents(@RequestBody(required=false) RetryRequest req) { ... }
}

record OutboxStatsResponse(long pending, long published, long failed, long dead) {}
record RetryRequest(List<UUID> ids) {}
```

---

### 3.6 payment 서비스: SlackNotificationService 교체

**삭제**: `payment/src/main/java/com/live_commerce/payment/infrastructure/notification/SlackNotificationService.java`

**변경**: `PaymentDLQConsumer.java` import 교체
```java
// Before:
import com.live_commerce.payment.infrastructure.notification.SlackNotificationService;
// After:
import com.live_commerce.common.notification.SlackNotificationService;
```

> API (sendCriticalAlert, sendWarningAlert) 동일 → 다른 코드 변경 없음

---

## 4. ADR (Architecture Decision Records)

### ADR-1: SlackNotificationService를 common-lib으로 이동

**결정**: common-lib의 `common.notification` 패키지로 이동

**이유**:
- common-lib에 이미 `spring-boot-starter-web` 의존성 존재 (RestTemplate 가능)
- `OutboxEventPublisher`(common-lib)에서 직접 사용하려면 같은 모듈이어야 함
- 모든 서비스에 자동 제공 (`@ConditionalOnProperty`로 선택적 활성화)

**대안**: 각 서비스에서 중복 구현 → DRY 원칙 위반

### ADR-2: MeterRegistry 필수 주입 (required = true)

**결정**: `MeterRegistry`를 생성자 주입 (필수)

**이유**:
- Spring Boot Actuator가 있는 모든 서비스에 자동으로 `MeterRegistry` 빈 등록됨
- order, payment 서비스 모두 `spring-boot-starter-actuator` 의존성 있음
- 테스트에서는 `SimpleMeterRegistry` 사용

**대안**: `@Autowired(required=false)` → 메트릭 수집 누락 위험

### ADR-3: Slack 알림은 선택적 (`@Autowired(required=false)`)

**결정**: `SlackNotificationService`는 null 체크로 처리

**이유**:
- `@ConditionalOnProperty("notification.slack.enabled")` → URL 미설정 시 빈 없음
- 알림 실패가 Outbox 발행을 중단시키면 안 됨

### ADR-4: OutboxAdminController는 order 서비스에만

**결정**: order 서비스에 추가 (payment는 미추가)

**이유**:
- order가 Saga 코디네이터이므로 운영 API의 자연스러운 위치
- `SagaAdminController` 패턴과 일관성 유지
- payment에 추가가 필요하면 동일 패턴으로 별도 추가

### ADR-5: findRetryableFailedEvents() 불일치 수정

**결정**: `retryCount < 5` → `retryCount < 3` 으로 변경

**이유**: `OutboxEvent.canRetry()` 기준(3회)과 일치시킴

---

## 5. 구현 순서

```
Step 1. common-lib/build.gradle → micrometer-core 추가
Step 2. common-lib: SlackNotificationService 생성 (common.notification 패키지)
Step 3. common-lib: OutboxEventRepository 메서드 4개 추가 + 쿼리 수정
Step 4. common-lib: OutboxEventPublisher MeterRegistry/Slack 통합
Step 5. payment: SlackNotificationService 삭제 + import 교체
Step 6. order: OutboxAdminController 추가
Step 7. 빌드 및 테스트
```

---

## 6. 테스트 계획

| 테스트 | 방법 | 확인 항목 |
|--------|------|----------|
| 메트릭 등록 | `SimpleMeterRegistry` 사용 | 5개 메트릭이 registry에 등록됨 |
| Dead Letter 알림 | `OutboxEventPublisher` 단위테스트, SlackService mock | retryCount=3 이벤트 → sendCriticalAlert 호출 |
| Slack 비활성 | SlackService=null | NPE 없이 정상 동작 |
| Admin API | `@WebMvcTest`, Repository mock | stats 응답 형식, retry 상태 변경 |

---

## 7. 완료 기준 (체크리스트)

- [ ] `micrometer-core` common-lib에 추가
- [ ] `SlackNotificationService` common-lib `common.notification` 패키지에 존재
- [ ] `OutboxEventPublisher`가 5개 메트릭을 `/actuator/prometheus`에 노출
- [ ] retryCount >= 3 이벤트 발생 시 `sendCriticalAlert()` 호출
- [ ] PENDING > 30 시 `sendWarningAlert()` 호출
- [ ] `GET /api/v1/admin/outbox/stats` 응답: `{"pending":N,"published":N,"failed":N,"dead":N}`
- [ ] `POST /api/v1/admin/outbox/retry` → FAILED 이벤트 PENDING 복귀 확인
- [ ] payment 서비스 `SlackNotificationService` 로컬 파일 삭제
- [ ] Slack URL 미설정 시 서비스 정상 기동
- [ ] `./gradlew :common-lib:compileJava :order:compileJava :payment:compileJava` 성공
