# payment-cleanup Design

> **Phase**: Design
>
> **Project**: Live Commerce Platform (MSA)
> **Service**: Payment Service (port: 19080)
> **Plan Reference**: [payment-cleanup.plan.md](../../01-plan/features/payment-cleanup.plan.md)
> **Created**: 2026-02-20

---

## 1. Architecture Overview

### 1.1 현재 vs 목표 구조

```
현재 (레거시 혼재)                     목표 (헥사고날 단일화)
─────────────────────────────────    ─────────────────────────────────
presentation/                         presentation/
  PaymentController     (@Deprecated) ← 삭제
  PaymentControllerV2   (@Deprecated) ← 삭제
  PaymentControllerV3   (헥사고날) ✅    PaymentControllerV3 (cancel 추가) ✅

application/service/                  application/
  PaymentService        (@Deprecated) ← 삭제    port/in/
  PaymentServiceV2      (@Deprecated) ← 삭제      ReadyPaymentUseCase ✅
  ReadyPaymentService   (헥사고날) ✅              ApprovePaymentUseCase ✅
  ApprovePaymentService (헥사고날) ✅              RefundPaymentUseCase ✅
  RefundPaymentService  (헥사고날) ✅              GetPaymentUseCase ✅
  GetPaymentService     (헥사고날) ✅              CompensatePaymentUseCase ✅
  CompensatePaymentService (헥사고날) ✅           CancelPaymentUseCase  ← 신규
                                        service/
application/port/                        ReadyPaymentService ✅
  KakaoPayClient        (@Deprecated) ← 삭제  ApprovePaymentService ✅
                                           RefundPaymentService ✅
infrastructure/kafka/consumer/            GetPaymentService ✅
  PaymentEventConsumer  (@Deprecated) ← 삭제  CompensatePaymentService ✅
                                           CancelPaymentService  ← 신규

infrastructure/adapter/in/kafka/      infrastructure/adapter/in/kafka/
  OrderFailedKafkaConsumer ✅             OrderFailedKafkaConsumer ✅
```

### 1.2 변경 요약

| 작업 | 파일 | 변경 유형 |
|------|------|-----------|
| CancelPaymentUseCase 추가 | `application/port/in/CancelPaymentUseCase.java` | 신규 생성 |
| CancelPaymentService 추가 | `application/service/CancelPaymentService.java` | 신규 생성 |
| PaymentControllerV3 cancel 추가 | `presentation/controller/PaymentControllerV3.java` | 수정 |
| PaymentController 삭제 | `presentation/controller/PaymentController.java` | 삭제 |
| PaymentControllerV2 삭제 | `presentation/controller/PaymentControllerV2.java` | 삭제 |
| PaymentService 삭제 | `application/service/PaymentService.java` | 삭제 |
| PaymentServiceV2 삭제 | `application/service/PaymentServiceV2.java` | 삭제 |
| KakaoPayClient 삭제 | `application/port/KakaoPayClient.java` | 삭제 |
| PaymentEventConsumer 삭제 | `infrastructure/kafka/consumer/PaymentEventConsumer.java` | 삭제 |
| 테스트 재작성 | 6개 테스트 파일 | 수정/재작성 |

---

## 2. New Use Case: CancelPaymentUseCase

### 2.1 인터페이스 설계

**파일**: `application/port/in/CancelPaymentUseCase.java`

```java
package com.live_commerce.payment.application.port.in;

import java.util.UUID;

/**
 * 결제 취소 유스케이스
 * - PENDING 상태의 결제를 CANCELED 상태로 전환
 * - 소유자 또는 MASTER 권한 필요
 */
public interface CancelPaymentUseCase {
    void cancel(CancelPaymentCommand command);

    record CancelPaymentCommand(
        UUID orderId,
        UUID userId,
        boolean hasMasterRole
    ) {
        public CancelPaymentCommand {
            if (orderId == null || userId == null) {
                throw new IllegalArgumentException("orderId와 userId는 필수입니다");
            }
        }
    }
}
```

**설계 근거:**
- `RefundPaymentUseCase`와 동일한 패턴 (orderId, userId, hasMasterRole)
- `orderId` 기반 조회 (V1/V2와 동일한 비즈니스 로직)
- 반환 타입 `void`: 취소는 상태 변경이므로 응답 바디 불필요 (V1/V2와 동일)

### 2.2 서비스 구현 설계

**파일**: `application/service/CancelPaymentService.java`

```java
package com.live_commerce.payment.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.payment.application.exception.CustomException;
import com.live_commerce.payment.application.exception.PaymentExceptionCode;
import com.live_commerce.payment.application.port.in.CancelPaymentUseCase;
import com.live_commerce.payment.application.port.out.LoadPaymentPort;
import com.live_commerce.payment.application.port.out.SavePaymentPort;
import com.live_commerce.payment.domain.model.Payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 취소 서비스
 * - 단일 책임: 결제 취소 유스케이스만 처리
 * - PENDING → CANCELED 상태 전이 (도메인에서 검증)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelPaymentService implements CancelPaymentUseCase {

    private final LoadPaymentPort loadPaymentPort;
    private final SavePaymentPort savePaymentPort;

    @Override
    @Transactional
    public void cancel(CancelPaymentCommand command) {
        Payment payment = loadPaymentPort.loadByOrderId(command.orderId())
            .orElseThrow(() -> new CustomException(PaymentExceptionCode.NOT_FOUND));

        // 권한 검증: 소유자 또는 MASTER
        if (!payment.belongsToUser(command.userId()) && !command.hasMasterRole()) {
            throw new CustomException(PaymentExceptionCode.UNAUTHORIZED);
        }

        // 도메인 상태 전이 (PENDING이 아니면 Payment.cancel()이 IllegalStateException 발생)
        payment.cancel();
        savePaymentPort.save(payment);

        log.info("[Payment] 결제 취소 완료 - orderId: {}", command.orderId());
    }
}
```

**설계 근거:**
- `LoadPaymentPort` + `SavePaymentPort`만 의존 (외부 결제 게이트웨이 호출 없음)
- 도메인 `Payment.cancel()` 이 PENDING 상태 검증 (`IllegalStateException` → 상위에서 `INVALID_STATUS`로 처리)
- `@Transactional` 보장으로 취소 실패 시 롤백

**도메인 `Payment.cancel()` 동작 확인:**
```java
// Payment.java (기존 코드 - 변경 없음)
public void cancel() {
    if (this.status != PaymentStatus.PENDING) {
        throw new IllegalStateException(
            String.format("대기 중인 결제만 취소 가능합니다. 현재 상태: %s", this.status)
        );
    }
    this.status = PaymentStatus.CANCELED;
}
```

**예외 처리 전략:**
```
Payment.cancel() → IllegalStateException
    → GlobalExceptionHandler에서 INVALID_STATUS로 매핑
    (기존 V1/V2와 동일한 동작)
```

---

## 3. PaymentControllerV3 수정

### 3.1 cancel 엔드포인트 추가

**파일**: `presentation/controller/PaymentControllerV3.java`

**현재:**
```java
@RequiredArgsConstructor
public class PaymentControllerV3 {
    private final ReadyPaymentUseCase readyPaymentUseCase;
    private final ApprovePaymentUseCase approvePaymentUseCase;
    private final RefundPaymentUseCase refundPaymentUseCase;
    private final GetPaymentUseCase getPaymentUseCase;
    // ... (cancel 없음)
}
```

**수정 후:**
```java
@RequiredArgsConstructor
public class PaymentControllerV3 {
    private final ReadyPaymentUseCase readyPaymentUseCase;
    private final ApprovePaymentUseCase approvePaymentUseCase;
    private final RefundPaymentUseCase refundPaymentUseCase;
    private final GetPaymentUseCase getPaymentUseCase;
    private final CancelPaymentUseCase cancelPaymentUseCase;  // ← 추가

    // ... 기존 메서드 ...

    @PostMapping("/{orderId}/cancel")                          // ← 추가
    public ResponseEntity<ApiResponse<Void>> cancelPayment(
        @PathVariable UUID orderId,
        @AuthenticationPrincipal RequestUserDetails userDetails
    ) {
        cancelPaymentUseCase.cancel(
            new CancelPaymentCommand(
                orderId,
                userDetails.getUserId(),
                hasMasterRole(userDetails)
            )
        );
        return ResponseUtil.noContent();
    }
}
```

**V1/V2와의 API 호환성:**
- 경로: `/api/v3/payments/{orderId}/cancel` (V1/V2는 각각 `/v1`, `/v2`)
- 요청 바디: 없음 (PathVariable만 사용)
- 응답: `204 No Content` (V1/V2와 동일)
- 인증: `@AuthenticationPrincipal RequestUserDetails` (V1/V2와 동일)

---

## 4. 삭제 대상 파일 상세

### 4.1 삭제 파일 목록

| 파일 | 이유 | 대체 |
|------|------|------|
| `PaymentController.java` | `@Deprecated(forRemoval = true)` | `PaymentControllerV3` |
| `PaymentControllerV2.java` | `@Deprecated(forRemoval = true)` | `PaymentControllerV3` |
| `PaymentService.java` | `@Deprecated(forRemoval = true)` | 4개 UseCase 서비스 |
| `PaymentServiceV2.java` | `@Deprecated(forRemoval = true)` | 4개 UseCase 서비스 |
| `KakaoPayClient.java` | `@Deprecated(forRemoval = true)` | `PaymentGatewayPort` |
| `PaymentEventConsumer.java` | `@Component` 비활성화됨, `@Deprecated` | `OrderFailedKafkaConsumer` |

### 4.2 삭제 전 의존성 확인

```
PaymentController
  └─ PaymentService (삭제됨)

PaymentControllerV2
  └─ PaymentServiceV2 (삭제됨)

PaymentService
  ├─ KakaoPayClient (삭제됨)
  ├─ OrderClient
  ├─ RedissonClient
  └─ PaymentJpaRepository

PaymentServiceV2
  ├─ KakaoPayClient (삭제됨)
  ├─ OrderClient
  ├─ RedissonClient
  ├─ PaymentJpaRepository
  └─ PaymentEventProducer

KakaoPayClient (interface)
  └─ KakaoPayClientImpl (구현체, 유지)
       └─ RestTemplate / WebClient

PaymentEventConsumer
  └─ PaymentServiceV2 (삭제됨)
  ※ @Component 이미 주석처리 → 빈 등록 안 됨
```

### 4.3 삭제 영향 체인

```
Step 1: PaymentController.java 삭제
  → PaymentService 의존 제거됨

Step 2: PaymentControllerV2.java 삭제
  → PaymentServiceV2 의존 제거됨

Step 3: PaymentEventConsumer.java 삭제
  → PaymentServiceV2 의존 제거됨

Step 4: PaymentService.java 삭제
  → KakaoPayClient 의존 제거됨 (1/2)

Step 5: PaymentServiceV2.java 삭제
  → KakaoPayClient 의존 제거됨 (2/2)

Step 6: KakaoPayClient.java 삭제
  → 모든 의존 제거됨, 컴파일 검증
```

---

## 5. 테스트 재설계

### 5.1 테스트별 변경 계획

| 테스트 파일 | 현재 상태 | 변경 계획 |
|------------|----------|----------|
| `PaymentApplicationTests.java` | `@ActiveProfiles("test")` 누락 | 어노테이션 추가 |
| `PaymentServiceTest.java` | `PaymentService` (deprecated) 테스트 | `UseCase 서비스` 기반으로 재작성 |
| `PaymentDuplicateRequestTest.java` | `PaymentService.readyPayment()` 테스트 | `ReadyPaymentUseCase` 기반으로 재작성 |
| `PaymentReattemptTest.java` | `PaymentService.readyPayment()` 테스트 | `ReadyPaymentUseCase` 기반으로 재작성 |
| `PaymentDistributedLockTest.java` | `@MockitoBean KakaoPayClient` | `@MockitoBean PaymentGatewayPort`로 교체 |
| `PaymentEventConsumerTest.java` | `PaymentEventConsumer` + `PaymentServiceV2` 테스트 | `OrderFailedKafkaConsumer` + `CompensatePaymentUseCase` 기반 재작성 |
| `PaymentRetryTemplateTest.java` | `PaymentService` 테스트 | `ReadyPaymentUseCase` 기반으로 재작성 |

### 5.2 PaymentServiceTest.java 재설계

**목표**: 레거시 `PaymentService` 대신 `GetPaymentService`, `RefundPaymentService`, `CancelPaymentService` 테스트

```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentServiceTest {

    // UseCase 서비스 (포트로 주입)
    @Autowired GetPaymentUseCase getPaymentUseCase;
    @Autowired RefundPaymentUseCase refundPaymentUseCase;
    @Autowired CancelPaymentUseCase cancelPaymentUseCase;

    @Autowired PaymentJpaRepository paymentJpaRepository;

    // 외부 어댑터 모킹
    @MockitoBean PaymentGatewayPort paymentGatewayPort;
    @MockitoBean PublishPaymentEventPort publishPaymentEventPort;

    // 테스트 케이스:
    // - getPayment_byOwner_success()
    // - getPayment_byMaster_success()
    // - getPayment_byUnauthorizedUser_fail()
    // - getPayments_byOwner_paginated_success()
    // - getPayments_byMaster_all_success()
    // - cancelPayment_pending_success()
    // - cancelPayment_byMaster_success()
    // - cancelPayment_alreadyCompleted_fail()
    // - cancelPayment_unauthorizedUser_fail()
    // - refundPayment_completed_success()
    // - refundPayment_byMaster_success()
    // - refundPayment_notCompleted_fail()
    // - refundPayment_unauthorizedUser_fail()
}
```

### 5.3 PaymentDuplicateRequestTest.java 재설계

**목표**: `ReadyPaymentUseCase` 기반 중복 결제 방지 테스트

```java
@SpringBootTest
@ActiveProfiles("test")
class PaymentDuplicateRequestTest {

    @Autowired ReadyPaymentUseCase readyPaymentUseCase;
    @Autowired PaymentJpaRepository paymentJpaRepository;

    @MockitoBean PaymentGatewayPort paymentGatewayPort;

    @BeforeEach
    void setUp() {
        KakaoPayReadyDto mockDto = ...; // 모킹
        when(paymentGatewayPort.ready(any(), any(), any(), any()))
            .thenReturn(new PaymentGatewayPort.PaymentReadyResult(...));
    }

    @Test
    void duplicatePaymentRequest_should_create_only_one_payment() {
        // 동시 5개 스레드 ReadyPaymentCommand 실행
        // 결제 1개만 생성되어야 함
    }
}
```

### 5.4 PaymentDistributedLockTest.java 수정

**최소 수정**: `@MockitoBean KakaoPayClient` → `@MockitoBean PaymentGatewayPort`

```java
// 기존
@MockitoBean
private KakaoPayClient kakaoPayClient;

// 변경
@MockitoBean
private PaymentGatewayPort paymentGatewayPort;
```

**나머지 테스트 로직은 Redisson 직접 테스트이므로 유지.**

### 5.5 PaymentEventConsumerTest.java 재설계

**목표**: `OrderFailedKafkaConsumer` + `CompensatePaymentUseCase` 기반 테스트

```java
class OrderFailedKafkaConsumerTest {  // 파일명 변경

    private CompensatePaymentUseCase compensatePaymentUseCase;
    private OrderFailedKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        compensatePaymentUseCase = mock(CompensatePaymentUseCase.class);
        consumer = new OrderFailedKafkaConsumer(compensatePaymentUseCase);
    }

    @Test
    void shouldCallCompensateOnOrderFailedEvent() {
        // given
        OrderFailedEvent event = new OrderFailedEvent(orderId, "재고 부족");
        // when
        consumer.listenOrderFailed(event, "order-failed", 0L, null);
        // then
        verify(compensatePaymentUseCase).compensate(
            new CompensatePaymentCommand(orderId, "재고 부족")
        );
    }
}
```

### 5.6 PaymentReattemptTest.java 재설계

**목표**: `ReadyPaymentUseCase` 기반 실패 후 재결제 테스트

```java
@SpringBootTest
@ActiveProfiles("test")
class PaymentReattemptTest {

    @Autowired ReadyPaymentUseCase readyPaymentUseCase;
    @Autowired PaymentJpaRepository paymentJpaRepository;

    @MockitoBean PaymentGatewayPort paymentGatewayPort;

    @Test
    void failedPayment_should_allow_repayment_on_same_orderId() {
        // 1차 시도: paymentGatewayPort.ready() 예외 발생
        when(paymentGatewayPort.ready(...)).thenThrow(RuntimeException);
        readyPaymentUseCase.ready(command); // 실패

        // 저장 여부 확인 (실패 시 저장 안 됨)
        assertTrue(paymentJpaRepository.findByOrderId(orderId).isEmpty());

        // 2차 시도: 정상 응답
        when(paymentGatewayPort.ready(...)).thenReturn(successResult);
        readyPaymentUseCase.ready(command2); // 성공

        // 저장 확인
        assertTrue(paymentJpaRepository.findByOrderId(orderId).isPresent());
    }
}
```

### 5.7 PaymentRetryTemplateTest.java 처리

`PaymentRetryTemplateTest`는 `PaymentService` + `RestTemplate` 모킹으로 구성됨. 레거시 서비스 삭제 후에는 제거 또는 내용을 `ReadyPaymentUseCase` 기반으로 재작성.

---

## 6. 모킹 전략 변경 (Before vs After)

### 6.1 레거시 모킹

```java
// 레거시: 구체 클래스 직접 모킹
@MockitoBean KakaoPayClient kakaoPayClient;

when(kakaoPayClient.requestKakaoPayReady(any(), any(), any(), any()))
    .thenReturn(new KakaoPayReadyDto(...));
```

### 6.2 신규 모킹 (Port 기반)

```java
// 신규: 포트 인터페이스 모킹 (기술 독립적)
@MockitoBean PaymentGatewayPort paymentGatewayPort;

when(paymentGatewayPort.ready(any(), any(), any(), any()))
    .thenReturn(new PaymentGatewayPort.PaymentReadyResult("T123456789", "https://..."));
```

**장점:**
- 구체 구현(KakaoPay)과 독립적
- 결제 게이트웨이 변경 시 테스트 변경 불필요
- 포트 계약만 검증

---

## 7. 아키텍처 결정 기록 (ADR)

### ADR-1: CancelPaymentUseCase 반환 타입

| 항목 | 결정 |
|------|------|
| 문제 | cancel의 반환 타입을 `void` vs `CancelPaymentResult`로 할지 |
| 결정 | `void` 유지 |
| 근거 | V1/V2 모두 `void` 반환, 취소 성공은 `204 No Content`로 충분 |

### ADR-2: Payment.cancel() IllegalStateException 처리

| 항목 | 결정 |
|------|------|
| 문제 | PENDING이 아닌 결제 취소 시 예외 처리 방식 |
| 결정 | 도메인 `IllegalStateException`을 `GlobalExceptionHandler`에서 `INVALID_STATUS`로 매핑 |
| 근거 | 기존 패턴과 일관성 유지. `CancelPaymentService`에서 별도 status 검사 불필요 |

### ADR-3: PaymentRetryTemplateTest 처리 방식

| 항목 | 결정 |
|------|------|
| 문제 | RetryTemplate 테스트를 유지할지, 삭제할지 |
| 결정 | `ReadyPaymentUseCase` 기반으로 재작성 (RetryConfig 자체는 유지됨) |
| 근거 | Retry 로직은 `KakaoPayGatewayAdapter`에도 적용될 수 있음. 테스트 가치 있음 |

### ADR-4: PaymentEventConsumerTest 파일명

| 항목 | 결정 |
|------|------|
| 문제 | 기존 `PaymentEventConsumerTest.java`를 수정할지, 새 파일로 만들지 |
| 결정 | 파일을 `OrderFailedKafkaConsumerTest.java`로 재작성 (기존 파일 내용 교체) |
| 근거 | 단순 내용 교체로 충분, 신규 파일 생성보다 명확 |

### ADR-5: KakaoPayClientImpl 유지 여부

| 항목 | 결정 |
|------|------|
| 문제 | `KakaoPayClient` 인터페이스 삭제 시 `KakaoPayClientImpl`도 삭제할지 |
| 결정 | `KakaoPayClientImpl`은 유지 (그러나 `KakaoPayGatewayAdapter`에서 내부적으로 사용) |
| 근거 | `KakaoPayClientImpl`은 실제 KakaoPay HTTP 호출 로직을 담당. 인터페이스(`KakaoPayClient`)만 삭제. |

---

## 8. 파일 구조 목표

```
payment/src/main/java/com/live_commerce/payment/
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── ApprovePaymentUseCase.java  ✅ 유지
│   │   │   ├── CancelPaymentUseCase.java   ✅ 신규
│   │   │   ├── CompensatePaymentUseCase.java ✅ 유지
│   │   │   ├── GetPaymentUseCase.java       ✅ 유지
│   │   │   ├── ReadyPaymentUseCase.java     ✅ 유지
│   │   │   └── RefundPaymentUseCase.java    ✅ 유지
│   │   │   [KakaoPayClient.java]            ← 삭제
│   │   └── out/
│   │       ├── LoadPaymentPort.java         ✅ 유지
│   │       ├── ManagePaymentExpirationPort.java ✅ 유지
│   │       ├── PaymentGatewayPort.java      ✅ 유지
│   │       ├── PublishPaymentEventPort.java  ✅ 유지
│   │       └── SavePaymentPort.java         ✅ 유지
│   └── service/
│       ├── ApprovePaymentService.java       ✅ 유지
│       ├── CancelPaymentService.java        ✅ 신규
│       ├── CompensatePaymentService.java    ✅ 유지
│       ├── GetPaymentService.java           ✅ 유지
│       ├── ReadyPaymentService.java         ✅ 유지
│       └── RefundPaymentService.java        ✅ 유지
│       [PaymentService.java]                ← 삭제
│       [PaymentServiceV2.java]              ← 삭제
│
├── infrastructure/
│   ├── adapter/
│   │   ├── in/
│   │   │   └── kafka/
│   │   │       └── OrderFailedKafkaConsumer.java ✅ 유지
│   │   ├── gateway/
│   │   │   └── KakaoPayGatewayAdapter.java  ✅ 유지
│   │   └── persistence/
│   │       └── (기존 유지)                  ✅ 유지
│   └── kafka/
│       └── consumer/
│           [PaymentEventConsumer.java]       ← 삭제
│
└── presentation/
    └── controller/
        └── PaymentControllerV3.java         ✅ 수정 (cancel 추가)
        [PaymentController.java]             ← 삭제
        [PaymentControllerV2.java]           ← 삭제
```

---

## 9. 구현 체크리스트

### Phase 1: 신규 UseCase 추가 (선행)

- [ ] `CancelPaymentUseCase.java` 생성
- [ ] `CancelPaymentService.java` 생성
- [ ] `PaymentControllerV3.java` - cancel 엔드포인트 추가
- [ ] 컴파일 검증 (`./gradlew :payment:compileJava`)

### Phase 2: 레거시 삭제 (순서 중요)

- [ ] `PaymentController.java` 삭제
- [ ] `PaymentControllerV2.java` 삭제
- [ ] `PaymentEventConsumer.java` 삭제
- [ ] `PaymentService.java` 삭제
- [ ] `PaymentServiceV2.java` 삭제
- [ ] `KakaoPayClient.java` 삭제
- [ ] 컴파일 검증 (`./gradlew :payment:compileJava`)

### Phase 3: 테스트 재작성

- [ ] `PaymentApplicationTests.java` - `@ActiveProfiles("test")` 추가
- [ ] `PaymentServiceTest.java` - UseCase 서비스 기반 재작성 (13개 테스트)
- [ ] `PaymentDuplicateRequestTest.java` - `ReadyPaymentUseCase` 기반 재작성
- [ ] `PaymentDistributedLockTest.java` - `@MockitoBean KakaoPayClient` → `PaymentGatewayPort` 교체
- [ ] `PaymentEventConsumerTest.java` - `OrderFailedKafkaConsumer` + `CompensatePaymentUseCase` 재작성
- [ ] `PaymentReattemptTest.java` - `ReadyPaymentUseCase` 기반 재작성
- [ ] `PaymentRetryTemplateTest.java` - `ReadyPaymentUseCase` 기반 재작성
- [ ] 테스트 전체 통과 (`./gradlew :payment:test`)

---

## 10. 검증 기준 (Gap Analysis 항목)

| ID | 검증 항목 | 기준 |
|----|----------|------|
| V-01 | `CancelPaymentUseCase.java` 존재 | `domain/port/in/` 위치 |
| V-02 | `CancelPaymentService.java` 존재 | `CancelPaymentUseCase` 구현 |
| V-03 | `PaymentControllerV3` cancel 엔드포인트 | `POST /{orderId}/cancel` 존재 |
| V-04 | `PaymentController.java` 부재 | 파일 삭제됨 |
| V-05 | `PaymentControllerV2.java` 부재 | 파일 삭제됨 |
| V-06 | `PaymentService.java` 부재 | 파일 삭제됨 |
| V-07 | `PaymentServiceV2.java` 부재 | 파일 삭제됨 |
| V-08 | `KakaoPayClient.java` 부재 | 파일 삭제됨 |
| V-09 | `PaymentEventConsumer.java` 부재 | 파일 삭제됨 |
| V-10 | `PaymentApplicationTests` 프로파일 | `@ActiveProfiles("test")` 포함 |
| V-11 | `PaymentServiceTest` - UseCase 사용 | `PaymentService` 의존 없음 |
| V-12 | `PaymentDuplicateRequestTest` - UseCase 사용 | `KakaoPayClient` 의존 없음 |
| V-13 | `PaymentDistributedLockTest` - Port 모킹 | `PaymentGatewayPort` 모킹 |
| V-14 | `PaymentEventConsumerTest` - 신규 컨슈머 | `OrderFailedKafkaConsumer` 테스트 |
| V-15 | 전체 테스트 통과 | `./gradlew :payment:test` PASS |
