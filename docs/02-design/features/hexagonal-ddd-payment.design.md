# Design: Payment 서비스 헥사고날 아키텍처 + DDD 적용

## 개요

- **Feature**: hexagonal-ddd-payment
- **대상 서비스**: `payment/`
- **작성일**: 2026-02-14
- **참조 Plan**: `docs/01-plan/features/hexagonal-ddd-domain-services.plan.md`
- **참조 패턴**: `order/` 서비스 hexagonal-ddd-pilot (91.4%), `coupon/` 서비스 hexagonal-ddd-coupon (92.1%)

---

## 현재 상태 분석 (As-Is)

Payment 서비스는 이미 hexagonal 포트/어댑터 구조가 **부분적으로** 구현되어 있다.
신규 Use Case 서비스와 포트는 존재하지만, 도메인 모델이 여전히 JPA에 오염된 상태다.

### 이미 완료된 작업 ✅

| 항목 | 파일 | 상태 |
|------|------|------|
| Inbound Port (UseCase) | `application/port/in/*UseCase.java` (4개) | ✅ 완료 |
| Outbound Port | `application/port/out/*.java` (5개) | ✅ 완료 |
| 신규 Application Service | `ReadyPaymentService`, `ApprovePaymentService`, `RefundPaymentService`, `GetPaymentService` | ✅ 완료 |
| Persistence Adapter | `adapter/persistence/PaymentPersistenceAdapter.java` | ✅ 완료 |
| Gateway Adapter | `adapter/gateway/KakaoPayGatewayAdapter.java` | ✅ 완료 |
| Event Adapter | `adapter/event/KafkaEventPublisherAdapter.java` | ✅ 완료 |
| Expiration Adapter | `adapter/event/RedisPaymentExpirationAdapter.java` | ✅ 완료 |
| Domain Events | `domain/event/Payment*DomainEvent.java` (3개) | ✅ 완료 |
| Domain Service | `domain/service/PaymentValidator.java` | ✅ 완료 |
| Payment 도메인 메서드 | `complete()`, `fail()`, `refund()`, `cancel()`, `of()` | ✅ 완료 |

### 남은 문제점 (To-Fix)

| 파일 | 문제 | 심각도 |
|------|------|--------|
| `domain/model/Payment.java` | `@Entity`, `@Table`, `@Column`, `@Id`, JPA annotations 포함 | High |
| `domain/model/Payment.java` | `BaseEntity` (JPA) 상속 | High |
| `domain/model/BaseEntity.java` | `@MappedSuperclass`, `@EntityListeners` JPA annotations | High |
| `domain/repository/PaymentRepository.java` | `JpaRepository` extends (도메인에 JPA 의존) | High |
| `domain/repository/PaymentQueryRepository.java` | QueryDSL Q-class, 도메인 레이어에 위치 | High |
| `domain/repository/PaymentQueryRepositoryImpl.java` | QueryDSL impl, 도메인 레이어에 위치 | High |
| `adapter/persistence/PaymentPersistenceAdapter.java` | `PaymentRepository` 직접 사용 (`Payment`가 @Entity인 상태) | Medium |
| `presentation/controller/PaymentController.java` | 레거시 `PaymentService` 사용 (v1) | Medium |
| `presentation/controller/PaymentControllerV2.java` | 레거시 `PaymentServiceV2` 사용 | Medium |
| `application/service/PaymentService.java` | 레거시 서비스, JPA 직접 의존 | Medium |
| `application/service/PaymentServiceV2.java` | 레거시 서비스, JPA 직접 의존 | Medium |
| `application/port/KakaoPayClient.java` | 구 포트 인터페이스, `application/port/`에 위치 | Low |
| `infrastructure/kafka/consumer/PaymentEventConsumer.java` | `PaymentServiceV2` 직접 호출 (보상 트랜잭션) | Medium |

---

## 목표 패키지 구조 (To-Be)

```
payment/src/main/java/com/live_commerce/payment/
├── domain/
│   ├── model/
│   │   ├── Payment.java              # 순수 Java (JPA 없음, @Entity 제거)
│   │   ├── PaymentStatus.java        # 그대로 유지
│   │   └── vo/
│   │       └── PaymentAmount.java    # [유지] 금액 VO
│   ├── port/
│   │   ├── in/                       # [이미 존재] - 위치는 application/port/in/ 유지
│   │   └── out/                      # [이미 존재] - 위치는 application/port/out/ 유지
│   ├── event/
│   │   ├── PaymentDomainEvent.java   # [유지]
│   │   ├── PaymentCompletedDomainEvent.java  # [유지]
│   │   └── PaymentFailedDomainEvent.java     # [유지]
│   ├── service/
│   │   └── PaymentValidator.java     # [유지]
│   └── exception/
│       └── PaymentDomainException.java  # [신규] 순수 도메인 예외 (선택)
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── ReadyPaymentUseCase.java    # [유지]
│   │   │   ├── ApprovePaymentUseCase.java  # [유지]
│   │   │   ├── RefundPaymentUseCase.java   # [유지]
│   │   │   ├── GetPaymentUseCase.java      # [유지]
│   │   │   └── CompensatePaymentUseCase.java  # [신규] 보상 트랜잭션 UseCase
│   │   └── out/
│   │       ├── LoadPaymentPort.java         # [유지]
│   │       ├── SavePaymentPort.java         # [유지]
│   │       ├── PaymentGatewayPort.java      # [유지]
│   │       ├── PublishPaymentEventPort.java # [유지]
│   │       └── ManagePaymentExpirationPort.java  # [유지]
│   ├── service/
│   │   ├── ReadyPaymentService.java       # [유지]
│   │   ├── ApprovePaymentService.java     # [유지]
│   │   ├── RefundPaymentService.java      # [유지]
│   │   ├── GetPaymentService.java         # [유지]
│   │   ├── CompensatePaymentService.java  # [신규] CompensatePaymentUseCase 구현
│   │   ├── PaymentService.java            # [유지] @Deprecated
│   │   └── PaymentServiceV2.java         # [유지] @Deprecated
│   ├── dto/
│   │   ├── request/ [유지]
│   │   └── response/ [유지]
│   └── exception/ [유지]
├── adapter/
│   ├── in/
│   │   └── kafka/
│   │       ├── OrderFailedKafkaConsumer.java  # [신규] CompensatePaymentUseCase 호출
│   │       └── PaymentDLQConsumer.java        # [이동 or 유지]
│   ├── out/
│   │   └── persistence/
│   │       ├── PaymentJpaEntity.java           # [신규] JPA 전용 엔티티
│   │       ├── BaseJpaEntity.java              # [신규] JPA 감사 베이스
│   │       ├── PaymentJpaRepository.java       # [이동] domain/repository → adapter
│   │       ├── PaymentQueryJpaRepository.java  # [이동] domain/repository/Impl → adapter
│   │       └── PaymentPersistenceAdapter.java  # [수정] PaymentJpaEntity 매퍼 추가
├── infrastructure/
│   ├── adapter/
│   │   ├── gateway/KakaoPayGatewayAdapter.java   # [유지]
│   │   ├── event/KafkaEventPublisherAdapter.java # [유지]
│   │   └── event/RedisPaymentExpirationAdapter.java # [유지]
│   ├── client/ [유지]
│   ├── kafka/
│   │   ├── consumer/PaymentEventConsumer.java   # @Deprecated 처리
│   │   └── producer/PaymentEventProducer.java   # [유지]
│   ├── config/ [유지]
│   ├── lock/ [유지]
│   └── security/ [유지]
├── presentation/
│   ├── controller/
│   │   ├── PaymentController.java     # [유지] @Deprecated (v1)
│   │   ├── PaymentControllerV2.java   # [유지] @Deprecated (v2)
│   │   └── PaymentControllerV3.java   # [신규] Use Case 인터페이스 사용
│   └── common/ [유지]
└── PaymentApplication.java
```

---

## 핵심 변경 사항 상세

### Step 1: Payment 도메인 모델 JPA 분리

**현재 문제:**
```java
// Payment.java (현재) - 도메인이 JPA에 의존
@Entity
@Table(name = "p_payment")
public class Payment extends BaseEntity {
    @Id @UuidGenerator
    private UUID id;
    @Column(nullable = false, unique = true)
    private UUID orderId;
    // ...
}
```

**목표 상태:**
```java
// Payment.java (목표) - 순수 Java 도메인 모델
public class Payment {
    private UUID id;
    private UUID orderId;
    private UUID userId;
    private BigDecimal amount;
    private PaymentStatus status;
    private String tid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 기존 도메인 메서드 유지 (complete, fail, refund, cancel, of)
    // @Transient 필드 유지 (domainEvents)
}
```

**신규 JPA 엔티티:**
```java
// PaymentJpaEntity.java (신규) - adapter/out/persistence/
@Entity
@Table(name = "p_payment")
public class PaymentJpaEntity extends BaseJpaEntity {
    @Id @UuidGenerator
    private UUID id;
    private UUID orderId;
    private UUID userId;
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
    private String tid;

    // 도메인 ↔ JPA 변환 메서드
    public static PaymentJpaEntity from(Payment domain) { ... }
    public Payment toDomain() { ... }
}
```

### Step 2: BaseEntity → BaseJpaEntity 분리

**BaseJpaEntity** (신규, `adapter/out/persistence/`)
```java
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseJpaEntity {
    @CreatedDate @Column(updatable = false)
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
    @Column(nullable = false)
    private boolean deletedStatus = false;
    private LocalDateTime deletedAt;
    @CreatedBy @Column(updatable = false)
    private String createdBy;
    @LastModifiedBy
    private String updatedBy;
    private String deletedBy;
}
```

**domain/model/BaseEntity.java** → `@Deprecated` 마킹

### Step 3: JPA Repository 이동

| 현재 위치 | 목표 위치 |
|----------|----------|
| `domain/repository/PaymentRepository.java` | `adapter/out/persistence/PaymentJpaRepository.java` |
| `domain/repository/PaymentQueryRepository.java` | `adapter/out/persistence/PaymentQueryJpaRepository.java` |
| `domain/repository/PaymentQueryRepositoryImpl.java` | `adapter/out/persistence/PaymentQueryJpaRepositoryImpl.java` |

**PaymentJpaRepository**: `JpaRepository<PaymentJpaEntity, UUID>` extends `PaymentQueryJpaRepository`

### Step 4: PaymentPersistenceAdapter 수정

```java
// 현재: Payment (도메인=JPA엔티티) 직접 사용
public class PaymentPersistenceAdapter implements LoadPaymentPort, SavePaymentPort {
    private final PaymentRepository paymentRepository;  // 변경 필요

    public Payment save(Payment payment) {
        return paymentRepository.save(payment);  // 변경 필요
    }
}

// 목표: PaymentJpaEntity ↔ Payment 매핑
public class PaymentPersistenceAdapter implements LoadPaymentPort, SavePaymentPort {
    private final PaymentJpaRepository paymentJpaRepository;

    public Payment save(Payment payment) {
        PaymentJpaEntity entity = PaymentJpaEntity.from(payment);
        PaymentJpaEntity saved = paymentJpaRepository.save(entity);
        return saved.toDomain();
    }

    public Optional<Payment> loadById(UUID id) {
        return paymentJpaRepository.findById(id).map(PaymentJpaEntity::toDomain);
    }
}
```

### Step 5: CompensatePaymentUseCase 신규 추가

보상 트랜잭션 전용 Use Case (현재 `PaymentServiceV2.compensateRefundByOrderId()` 분리)

```java
// application/port/in/CompensatePaymentUseCase.java
public interface CompensatePaymentUseCase {
    void compensate(CompensatePaymentCommand command);

    record CompensatePaymentCommand(UUID orderId, String reason) {}
}

// application/service/CompensatePaymentService.java
@Service
public class CompensatePaymentService implements CompensatePaymentUseCase {
    private final LoadPaymentPort loadPaymentPort;
    private final SavePaymentPort savePaymentPort;
    private final PaymentGatewayPort paymentGatewayPort;

    public void compensate(CompensatePaymentCommand command) {
        // compensateRefundByOrderId 로직 이관
    }
}
```

### Step 6: 신규 Kafka 어댑터 (Inbound)

```java
// adapter/in/kafka/OrderFailedKafkaConsumer.java
@Component
public class OrderFailedKafkaConsumer {
    private final CompensatePaymentUseCase compensatePaymentUseCase;

    @KafkaListener(topics = "order-failed", groupId = "${spring.application.name}-hexagonal")
    public void listenOrderFailed(OrderFailedEvent event, Acknowledgment ack) {
        compensatePaymentUseCase.compensate(
            new CompensatePaymentCommand(event.orderId(), event.message())
        );
        ack.acknowledge();
    }
}
```

기존 `PaymentEventConsumer` → `@Deprecated` + `@Component` 주석 처리

### Step 7: 신규 컨트롤러 (PaymentControllerV3)

```java
// presentation/controller/PaymentControllerV3.java
@RestController
@RequestMapping("/api/v3/payments")
public class PaymentControllerV3 {
    private final ReadyPaymentUseCase readyPaymentUseCase;
    private final ApprovePaymentUseCase approvePaymentUseCase;
    private final RefundPaymentUseCase refundPaymentUseCase;
    private final GetPaymentUseCase getPaymentUseCase;

    // 기존 endpoint와 동일한 API, Use Case 호출로 변경
}
```

`PaymentController` (v1) → `@Deprecated`
`PaymentControllerV2` → `@Deprecated`

### Step 8: 레거시 서비스 및 포트 Deprecated 처리

| 파일 | 조치 |
|------|------|
| `PaymentService.java` | `@Deprecated(since="hexagonal-ddd-payment", forRemoval=true)` |
| `PaymentServiceV2.java` | `@Deprecated(since="hexagonal-ddd-payment", forRemoval=true)` |
| `application/port/KakaoPayClient.java` | `@Deprecated(since="hexagonal-ddd-payment", forRemoval=true)` |
| `domain/model/BaseEntity.java` | `@Deprecated(since="hexagonal-ddd-payment", forRemoval=true)` |
| `infrastructure/kafka/consumer/PaymentEventConsumer.java` | `@Component` 주석 처리 + `@Deprecated` |

---

## 구현 순서

| 순서 | 작업 | 파일 수 |
|------|------|--------|
| 1 | `BaseJpaEntity.java` 신규 생성 | 1 |
| 2 | `PaymentJpaEntity.java` 신규 생성 (도메인 ↔ JPA 매핑) | 1 |
| 3 | `Payment.java` JPA 어노테이션 제거 (순수 Java) | 1 |
| 4 | `domain/model/BaseEntity.java` → `@Deprecated` | 1 |
| 5 | `PaymentJpaRepository.java` 신규 생성 (adapter 위치) | 1 |
| 6 | `PaymentQueryJpaRepository.java` + Impl 이동 | 2 |
| 7 | `PaymentPersistenceAdapter.java` 수정 (JpaEntity 매핑) | 1 |
| 8 | `CompensatePaymentUseCase.java` + `CompensatePaymentService.java` 신규 | 2 |
| 9 | `OrderFailedKafkaConsumer.java` 신규 (adapter/in/kafka) | 1 |
| 10 | `PaymentEventConsumer.java` `@Component` 비활성화 + `@Deprecated` | 1 |
| 11 | `PaymentControllerV3.java` 신규 | 1 |
| 12 | 레거시 `@Deprecated` 처리 (`PaymentService`, `PaymentServiceV2`, `KakaoPayClient`, etc.) | 4 |

**총 변경 파일**: ~17개

---

## 검증 기준

| 검증 항목 | 기준 |
|----------|------|
| `Payment.java`에 JPA 의존 없음 | `import jakarta.persistence.*` 없음 |
| Domain layer에 JPA repository 없음 | `domain/repository/` 하위 파일 없음 |
| 새 Use Case 컨트롤러 응답 | `/api/v3/payments` 엔드포인트 동작 |
| 보상 트랜잭션 hexagonal | `OrderFailedKafkaConsumer` → `CompensatePaymentUseCase` 호출 |
| 레거시 비활성화 | `PaymentEventConsumer` `@Component` 없음 |
| 빌드 성공 | `./gradlew :payment:build` |

---

## 레거시 하위 호환성

- `PaymentController` (v1), `PaymentControllerV2` → `@Deprecated` but **활성 유지** (API breaking change 방지)
- `PaymentService`, `PaymentServiceV2` → `@Deprecated` but **빈 등록 유지** (컨트롤러 의존)
- `PaymentEventConsumer` → `@Component` 제거 (중복 소비 방지, 신규 `OrderFailedKafkaConsumer` 대체)
- `domain/repository/*.java` → 삭제하지 않고 `@Deprecated` 처리 (점진적 제거)

---

## 리스크 및 주의사항

1. **QueryDSL Q-class 재생성**: `PaymentQueryRepositoryImpl` 이동 시 `QPayment` → `QPaymentJpaEntity`로 변경 필요. `./gradlew :payment:compileJava` 선행
2. **분산 락 키**: `ReadyPaymentService`의 `@DistributedLock(key = "#command.orderId()")` - Command record 메서드 표현식 확인
3. **도메인 이벤트 유지**: `Payment.domainEvents` (`@Transient`) - JPA 엔티티에서 분리 후에도 도메인 모델에서 동작해야 함
4. **PaymentAmount VO**: `domain/model/vo/PaymentAmount.java` - 현재 `PaymentGatewayPort`의 inner record와 동일. 도메인 VO와 포트 결과 record를 분리 유지
