# payment-cleanup Plan

> **Phase**: Plan
>
> **Project**: Live Commerce Platform (MSA)
> **Service**: Payment Service (port: 19080)
> **Created**: 2026-02-19
> **Author**: PDCA Skill

---

## 1. Overview

### 1.1 Feature Summary

| Item | Content |
|------|---------|
| Feature ID | payment-cleanup |
| Feature Name | Payment Service 레거시 정리 및 개선 |
| Service | payment |
| Type | Code Cleanup + Feature Addition |
| Priority | High |

### 1.2 Background

`hexagonal-ddd-payment` PDCA 사이클(94.1% match rate) 완료 후, 다음 작업이 남아 있음:

- `@Deprecated(forRemoval = true)` 처리된 V1/V2 컨트롤러와 서비스가 빈으로 여전히 등록됨
- 헥사고날 V3 컨트롤러에 **`cancel` 엔드포인트가 누락됨** (V1/V2에는 있었음)
- 테스트가 레거시 `PaymentService` 기준으로 작성되어 있음 (신규 UseCase 서비스 미테스트)
- 레거시 코드를 실제로 삭제하지 않아 코드베이스 혼재 상태

---

## 2. Current State Analysis

### 2.1 존재하는 컨트롤러 버전

| 컨트롤러 | 경로 | 상태 | 의존 서비스 |
|----------|------|------|------------|
| `PaymentController` | `/api/v1/payments` | `@Deprecated` | `PaymentService` |
| `PaymentControllerV2` | `/api/v2/payments` | `@Deprecated` | `PaymentServiceV2` |
| `PaymentControllerV3` | `/api/v3/payments` | **현행** | 4 UseCase (헥사고날) |

### 2.2 엔드포인트 갭 분석

| 엔드포인트 | V1 | V2 | V3 | 비고 |
|-----------|:--:|:--:|:--:|------|
| `POST /ready` | ✅ | ✅ | ✅ | - |
| `POST /approve` | ✅ | ✅ | ✅ | - |
| `GET /{paymentId}` | ✅ | ✅ | ✅ | - |
| `GET /` (목록) | ✅ | ✅ | ✅ | - |
| `POST /{orderId}/refund` | ✅ | ✅ | ✅ | - |
| `POST /{orderId}/cancel` | ✅ | ✅ | **❌ 누락** | **추가 필요** |

### 2.3 레거시 파일 목록 (삭제 대상)

```
presentation/controller/
├── PaymentController.java          ← 삭제 (V1 deprecated)
├── PaymentControllerV2.java        ← 삭제 (V2 deprecated)

application/service/
├── PaymentService.java             ← 삭제 (deprecated)
├── PaymentServiceV2.java           ← 삭제 (deprecated)

application/port/
├── KakaoPayClient.java             ← 삭제 (레거시 포트, 현재는 KakaoPayGatewayAdapter)

infrastructure/kafka/consumer/
├── PaymentEventConsumer.java       ← 확인 후 처리 (레거시 consumer vs 새 adapter)
```

### 2.4 테스트 현황

| 테스트 파일 | 대상 | 문제점 |
|------------|------|--------|
| `PaymentServiceTest.java` | `PaymentService` (deprecated) | 레거시 서비스 테스트 |
| `PaymentDistributedLockTest.java` | 레거시 서비스 | 검토 필요 |
| `PaymentDuplicateRequestTest.java` | 레거시 서비스 | 검토 필요 |
| `PaymentEventConsumerTest.java` | 레거시 consumer | 검토 필요 |
| `PaymentReattemptTest.java` | 레거시 서비스 | 검토 필요 |
| `PaymentRetryTemplateTest.java` | 레거시 서비스 | 검토 필요 |
| `PaymentApplicationTests.java` | contextLoads | `@ActiveProfiles("test")` 누락 |

---

## 3. Objectives

### 3.1 Primary Goals

1. **레거시 코드 완전 제거** - Deprecated V1/V2 컨트롤러와 서비스 삭제
2. **CancelPaymentUseCase 추가** - V3 컨트롤러 cancel 엔드포인트 구현
3. **테스트 현대화** - 신규 UseCase 서비스 기준 테스트로 전환

### 3.2 Success Criteria

- [ ] `PaymentController`, `PaymentControllerV2` 삭제됨
- [ ] `PaymentService`, `PaymentServiceV2` 삭제됨
- [ ] `PaymentControllerV3`에 `POST /{orderId}/cancel` 엔드포인트 존재
- [ ] `CancelPaymentUseCase` 인터페이스 + `CancelPaymentService` 구현체 존재
- [ ] 모든 테스트가 신규 서비스 기준으로 통과
- [ ] `./gradlew :payment:test` 100% PASS

---

## 4. Scope

### 4.1 In Scope

| 항목 | 설명 |
|------|------|
| 레거시 컨트롤러 삭제 | `PaymentController` (V1), `PaymentControllerV2` (V2) |
| 레거시 서비스 삭제 | `PaymentService`, `PaymentServiceV2` |
| CancelPaymentUseCase 추가 | 포트 + 서비스 + V3 컨트롤러 엔드포인트 |
| 테스트 재작성 | 신규 UseCase 서비스(ReadyPaymentService 등) 기반 테스트 |
| `PaymentApplicationTests` 수정 | `@ActiveProfiles("test")` 추가 |
| 레거시 `KakaoPayClient` 포트 확인 | 사용 여부 검증 후 삭제 여부 결정 |

### 4.2 Out of Scope

| 항목 | 이유 |
|------|------|
| 결제 게이트웨이 변경 | 기능 범위 아님 |
| KakaoPay API 변경 | 외부 연동 유지 |
| `PaymentQueryJpaRepositoryImpl` 리네임 | 현재 `PaymentQueryJpaRepository + Impl` 방식이 작동 중 (Spring Data JPA 지원) |
| 새로운 결제 수단 추가 | 별도 PDCA 필요 |

---

## 5. Implementation Plan

### 5.1 Phase 1: 신규 Cancel UseCase 추가

**이유**: 레거시 삭제 전에 V3 기능을 완성해야 안전하게 마이그레이션 가능

```
domain/port/in/
└── CancelPaymentUseCase.java       ← 신규 (command record 포함)

application/service/
└── CancelPaymentService.java       ← 신규 (CancelPaymentUseCase 구현)

presentation/controller/
└── PaymentControllerV3.java        ← 수정 (cancel 엔드포인트 추가)
```

**CancelPaymentUseCase 설계:**
```java
public interface CancelPaymentUseCase {
    void cancel(CancelPaymentCommand command);

    record CancelPaymentCommand(UUID orderId, UUID userId, boolean isMaster) {}
}
```

**CancelPaymentService 설계:**
```java
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

        // 권한 검증: 소유자 또는 마스터
        if (!payment.belongsToUser(command.userId()) && !command.isMaster()) {
            throw new CustomException(PaymentExceptionCode.UNAUTHORIZED);
        }
        // 도메인 검증: PENDING 상태만 취소 가능
        payment.cancel();
        savePaymentPort.save(payment);
    }
}
```

### 5.2 Phase 2: 레거시 코드 삭제

삭제 대상 (7개 파일):
1. `presentation/controller/PaymentController.java`
2. `presentation/controller/PaymentControllerV2.java`
3. `application/service/PaymentService.java`
4. `application/service/PaymentServiceV2.java`
5. `application/port/KakaoPayClient.java` (레거시 포트, KakaoPayGatewayAdapter로 대체됨)
6. `infrastructure/kafka/consumer/PaymentEventConsumer.java` (→ 신규 `OrderFailedKafkaConsumer`로 대체됨 확인 후)
7. 기타 레거시 의존 DTO/클래스 검증

### 5.3 Phase 3: 테스트 재작성

**기존 (레거시 서비스 기반) → 신규 (UseCase 서비스 기반):**

```
PaymentServiceTest.java
  → ReadyPaymentServiceTest.java   (중복 결제 방지, 성공 케이스)
  → GetPaymentServiceTest.java     (소유자, 마스터, 권한없음)
  → CancelPaymentServiceTest.java  (성공, 상태 오류, 권한 오류)
  → RefundPaymentServiceTest.java  (성공, 상태 오류, 권한 오류)
```

또는 단일 `PaymentServiceTest.java`를 UseCase 기반으로 재작성.

**모크 전략:**
```java
@MockitoBean LoadPaymentPort loadPaymentPort;
@MockitoBean SavePaymentPort savePaymentPort;
@MockitoBean PaymentGatewayPort paymentGatewayPort;
@MockitoBean ManagePaymentExpirationPort managePaymentExpirationPort;
@MockitoBean PublishPaymentEventPort publishPaymentEventPort;
```

---

## 6. Risk Assessment

| 위험 | 수준 | 완화 방안 |
|------|:----:|----------|
| V1/V2 API를 직접 호출하는 클라이언트 | Low | forRemoval 태그가 있어 이미 공지됨, 프론트는 V3로 전환 완료 |
| 레거시 삭제 후 컴파일 오류 | Medium | 단계별 삭제 + 빌드 검증 |
| 테스트 중단 | Medium | 새 테스트 작성 후 레거시 삭제 |
| `PaymentEventConsumer` 레거시 여부 불분명 | Low | Design 단계에서 코드 분석 |

---

## 7. Acceptance Criteria

```
✅ 컴파일 성공 (./gradlew :payment:compileJava)
✅ 테스트 전체 통과 (./gradlew :payment:test)
✅ PaymentControllerV3 - cancel 엔드포인트 동작
✅ PaymentController(V1), PaymentControllerV2(V2) 클래스 파일 없음
✅ PaymentService, PaymentServiceV2 클래스 파일 없음
✅ Gap Analysis >= 90%
```

---

## 8. Next Steps

| 단계 | 명령 | 내용 |
|------|------|------|
| Design | `/pdca design payment-cleanup` | 상세 설계 (포트 인터페이스, 서비스 로직) |
| Do | 구현 | Phase 1 → 2 → 3 순서 |
| Check | `/pdca analyze payment-cleanup` | Gap Analysis |
| Report | `/pdca report payment-cleanup` | 완료 보고서 |
