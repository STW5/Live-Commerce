# Design: Coupon 서비스 헥사고날 아키텍처 + DDD 적용

## 개요

- **Feature**: hexagonal-ddd-coupon
- **대상 서비스**: `coupon/`
- **작성일**: 2026-02-14
- **참조 Plan**: `docs/01-plan/features/hexagonal-ddd-domain-services.plan.md`
- **참조 패턴**: `order/` 서비스 hexagonal-ddd-pilot (Match Rate 91.4%)

---

## 현재 상태 문제점 (As-Is 분석)

### 도메인 레이어 오염 목록

| 파일 | 문제 | 심각도 |
|------|------|--------|
| `CouponPolicy.java` | `@Entity`, `@Table` 직접 포함 | High |
| `CouponPolicy.java` | `presentation.dto.request.UpdateCouponPolicyRequest` import | High |
| `IssuedCoupon.java` | `@Entity` 직접 포함 | High |
| `IssuedCoupon.java` | `infrastructure.security.RequestUserDetails` import | High |
| `IssuedCoupon.java` | `presentation.dto.request.IssuedCouponRequest` import | High |
| `CouponPolicyRepository` | `JpaRepository` 직접 extends (도메인이 JPA 알고 있음) | High |
| `IssuedCouponRepository` | `JpaRepository` 직접 extends | High |
| `IssueFirstJoinCouponPort` | `application/port/`에 위치 (`domain/port/out/`이어야 함) | Medium |
| `PublishCouponUsedEventPort` | `application/port/`에 위치, `@Component` 어노테이션 오용 | Medium |
| `IssuedCouponService` | `OrderClient` (Feign) 직접 주입 | Medium |
| `IssuedCouponService` | `infrastructure.security.RequestUserDetails` import | Medium |

---

## 목표 패키지 구조 (To-Be)

```
coupon/src/main/java/com/live_commerce/coupon/
├── domain/
│   ├── model/
│   │   ├── CouponPolicy.java          # 순수 Java (JPA 없음)
│   │   ├── IssuedCoupon.java          # 순수 Java (JPA 없음)
│   │   ├── DISCOUNT_TYPE.java         # 그대로 유지
│   │   └── vo/
│   │       ├── CouponCode.java        # [신규] 쿠폰 코드 VO
│   │       └── CouponPeriod.java      # [신규] 유효 기간 VO
│   ├── port/
│   │   ├── in/
│   │   │   ├── IssueCouponUseCase.java          # [신규]
│   │   │   ├── UseCouponUseCase.java             # [신규]
│   │   │   ├── RestoreCouponUseCase.java         # [신규] 보상 트랜잭션
│   │   │   └── ManageCouponPolicyUseCase.java    # [신규]
│   │   └── out/
│   │       ├── CouponPolicyRepositoryPort.java   # [신규]
│   │       ├── IssuedCouponRepositoryPort.java   # [신규]
│   │       ├── OrderQueryPort.java               # [신규] 보상 트랜잭션용 Order 조회
│   │       └── CouponEventPublisher.java         # [신규] (기존 Port 재배치)
│   └── exception/
│       ├── CouponDomainException.java            # [신규] 순수 도메인 예외
│       ├── CouponDiscountTypeException.java      # 그대로 유지
│       ├── CouponPolicyException.java            # 그대로 유지
│       └── IssuedCouponException.java            # 그대로 유지
├── application/
│   ├── service/
│   │   ├── IssueCouponService.java              # [신규] IssueCouponUseCase 구현
│   │   ├── UseCouponService.java                # [신규] UseCouponUseCase 구현
│   │   ├── RestoreCouponService.java            # [신규] RestoreCouponUseCase 구현
│   │   ├── ManageCouponPolicyService.java       # [신규] ManageCouponPolicyUseCase 구현
│   │   ├── CouponPolicyService.java             # [유지] @Deprecated
│   │   └── IssuedCouponService.java             # [유지] @Deprecated
│   ├── dto/
│   │   ├── command/
│   │   │   ├── IssueCouponCommand.java          # [신규]
│   │   │   ├── UseCouponCommand.java            # [신규]
│   │   │   └── CreateCouponPolicyCommand.java   # [신규]
│   │   └── result/
│   │       ├── IssuedCouponResult.java          # [신규]
│   │       └── CouponPolicyResult.java          # [신규]
│   └── ...
├── adapter/
│   ├── in/
│   │   └── kafka/
│   │       ├── FirstJoinCouponKafkaConsumer.java  # [신규] UseCase 주입
│   │       ├── CouponUsedKafkaConsumer.java       # [신규] UseCase 주입
│   │       └── OrderFailedKafkaConsumer.java      # [신규] UseCase 주입
│   └── out/
│       ├── persistence/
│       │   ├── BaseJpaEntity.java                 # [신규] Order 서비스 동일 패턴
│       │   ├── CouponPolicyJpaEntity.java         # [신규] @Entity 전담
│       │   ├── IssuedCouponJpaEntity.java         # [신규] @Entity 전담
│       │   ├── CouponPolicyJpaRepository.java     # [신규] JpaRepository extends
│       │   ├── IssuedCouponJpaRepository.java     # [신규] JpaRepository extends
│       │   ├── CouponPolicyMapper.java            # [신규]
│       │   ├── IssuedCouponMapper.java            # [신규]
│       │   ├── CouponPolicyPersistenceAdapter.java # [신규] CouponPolicyRepositoryPort 구현
│       │   └── IssuedCouponPersistenceAdapter.java # [신규] IssuedCouponRepositoryPort 구현
│       ├── messaging/
│       │   └── KafkaCouponEventPublisher.java     # [신규] CouponEventPublisher 구현
│       └── client/
│           └── OrderFeignAdapter.java             # [신규] OrderQueryPort 구현
└── ...
```

---

## 도메인 모델 설계

### CouponPolicy (순수 도메인 객체)

```java
package com.live_commerce.coupon.domain.model;

// JPA 없음, Presentation 없음, Infrastructure 없음
public class CouponPolicy {
    private String code;          // CouponCode VO로 감쌀 수도 있음
    private String name;
    private DISCOUNT_TYPE discountType;
    private BigDecimal discountValue;
    private BigDecimal minOrderAmt;
    private BigDecimal maxOrderAmt;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private boolean isActive;
    // Audit 필드 (JPA 없이 직접 관리)
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private String deletedBy;
    private LocalDateTime deletedAt;
    private Boolean deletedStatus = false;

    // 정적 팩토리
    public static CouponPolicy create(String code, String name, DISCOUNT_TYPE discountType,
                                       BigDecimal discountValue, BigDecimal minOrderAmt,
                                       BigDecimal maxOrderAmt, LocalDateTime startAt,
                                       LocalDateTime endAt, boolean isActive) { ... }

    public static CouponPolicy reconstitute(...) { ... }

    // 도메인 행위
    public void update(String name, DISCOUNT_TYPE discountType, BigDecimal discountValue,
                       BigDecimal minOrderAmt, BigDecimal maxOrderAmt,
                       LocalDateTime startAt, LocalDateTime endAt, boolean isActive) { ... }
    public void markAsDeleted(String deletedBy) { ... }
    public void validateDiscountType() { ... }

    // [제거] updateCouponPolicy(UpdateCouponPolicyRequest) → presentation 의존 제거
}
```

### IssuedCoupon (순수 도메인 객체)

```java
package com.live_commerce.coupon.domain.model;

// JPA 없음, RequestUserDetails 없음, IssuedCouponRequest 없음
public class IssuedCoupon {
    private UUID id;
    private UUID userId;
    private String couponCode;
    private Boolean isUsed;
    private LocalDateTime usedAt;
    private LocalDateTime expiresAt;

    // 정적 팩토리 (presentation/infrastructure 의존 없음)
    public static IssuedCoupon issue(UUID userId, String couponCode, LocalDateTime expiresAt) { ... }
    public static IssuedCoupon reconstitute(UUID id, UUID userId, String couponCode,
                                            Boolean isUsed, LocalDateTime usedAt,
                                            LocalDateTime expiresAt) { ... }

    // 도메인 행위 (그대로 유지)
    public void useCoupon() { ... }
    public void restoreCoupon() { ... }
}
```

### Value Objects

```java
// CouponCode - 쿠폰 코드 불변 VO
public record CouponCode(String value) {
    public CouponCode {
        if (value == null || value.isBlank())
            throw new CouponDomainException("쿠폰 코드는 필수입니다.");
    }
}

// CouponPeriod - 유효 기간 VO
public record CouponPeriod(LocalDateTime startAt, LocalDateTime endAt) {
    public CouponPeriod {
        if (startAt == null || endAt == null)
            throw new CouponDomainException("쿠폰 유효 기간은 필수입니다.");
        if (endAt.isBefore(startAt))
            throw new CouponDomainException("쿠폰 종료일은 시작일 이후여야 합니다.");
    }
    public boolean isValid() { return LocalDateTime.now().isBefore(endAt); }
}
```

---

## Port 인터페이스 설계

### Inbound Ports (UseCase)

```java
// IssueCouponUseCase
public interface IssueCouponUseCase {
    IssuedCouponResult issueCoupon(IssueCouponCommand command);
    IssuedCouponResult issueFirstJoinCoupon(UUID userId);
}

// UseCouponUseCase
public interface UseCouponUseCase {
    IssuedCouponResult useCoupon(UseCouponCommand command);
}

// RestoreCouponUseCase (보상 트랜잭션)
public interface RestoreCouponUseCase {
    void restoreCouponByOrderFailed(UUID orderId);
}

// ManageCouponPolicyUseCase
public interface ManageCouponPolicyUseCase {
    CouponPolicyResult createCouponPolicy(CreateCouponPolicyCommand command);
    CouponPolicyResult getCouponPolicy(String code);
    void updateCouponPolicy(String code, CreateCouponPolicyCommand command);
    void deleteCouponPolicy(String code);
}
```

### Outbound Ports

```java
// CouponPolicyRepositoryPort
public interface CouponPolicyRepositoryPort {
    CouponPolicy save(CouponPolicy couponPolicy);
    Optional<CouponPolicy> findByCode(String code);
    Optional<CouponPolicy> findActiveByCode(String code); // deletedStatus = false
    List<CouponPolicy> findAllActive();
}

// IssuedCouponRepositoryPort
public interface IssuedCouponRepositoryPort {
    IssuedCoupon save(IssuedCoupon issuedCoupon);
    Optional<IssuedCoupon> findByIdAndUserIdAndNotUsed(UUID couponId, UUID userId);
    Optional<IssuedCoupon> findByIdAndUserId(UUID couponId, UUID userId);
    List<IssuedCoupon> findByUserId(UUID userId);
}

// OrderQueryPort (보상 트랜잭션 - 주문 정보 조회)
public interface OrderQueryPort {
    OrderInfo getOrder(UUID orderId);
    record OrderInfo(UUID orderId, UUID userId, UUID couponId) {}
}

// CouponEventPublisher
public interface CouponEventPublisher {
    void publishCouponUsedEvent(UUID couponId, UUID userId);
    // publishFirstJoinEvent는 제거 (직접 발급으로 변경됨)
}
```

---

## Adapter 설계

### Persistence Adapter

**BaseJpaEntity** (Order 서비스 패턴 동일)
```java
// adapter/out/persistence/BaseJpaEntity.java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseJpaEntity {
    @CreatedBy private String createdBy;
    @CreatedDate private LocalDateTime createdAt;
    @LastModifiedBy private String updatedBy;
    @LastModifiedDate private LocalDateTime updatedAt;
    private String deletedBy;
    private LocalDateTime deletedAt;
    private Boolean deletedStatus = false;

    public void delete(String deletedBy) { ... }
}
```

**CouponPolicyJpaEntity**
```java
// adapter/out/persistence/CouponPolicyJpaEntity.java
@Entity
@Table(name = "p_coupon_policy")
public class CouponPolicyJpaEntity extends BaseJpaEntity {
    @Id private String code;
    private String name;
    @Enumerated(EnumType.STRING) private DISCOUNT_TYPE discountType;
    private BigDecimal discountValue;
    private BigDecimal minOrderAmt;
    private BigDecimal maxOrderAmt;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private boolean isActive;
}
```

**IssuedCouponJpaEntity**
```java
// adapter/out/persistence/IssuedCouponJpaEntity.java
@Entity
@Table(name = "p_issued_coupon")
public class IssuedCouponJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.AUTO) private UUID id;
    @Column(nullable = false) private UUID userId;
    @Column(nullable = false, updatable = false) private String couponCode;
    private Boolean isUsed;
    private LocalDateTime usedAt;
    @Column(nullable = false) private LocalDateTime expiresAt;
}
```

**Mapper 패턴**
```java
// CouponPolicyMapper
@Component
public class CouponPolicyMapper {
    public CouponPolicyJpaEntity toJpaEntity(CouponPolicy domain) { ... }
    public CouponPolicy toDomain(CouponPolicyJpaEntity entity) {
        return CouponPolicy.reconstitute(...);
    }
}
```

### Messaging Adapter

```java
// KafkaCouponEventPublisher - CouponEventPublisher 구현
@Component
public class KafkaCouponEventPublisher implements CouponEventPublisher {
    // 기존 PublishCouponUsedEventPort 로직을 여기로 이전
    public void publishCouponUsedEvent(UUID couponId, UUID userId) { ... }
}
```

### Client Adapter

```java
// OrderFeignAdapter - OrderQueryPort 구현
@Component
public class OrderFeignAdapter implements OrderQueryPort {
    private final OrderClient orderClient; // 기존 Feign Client 재사용

    public OrderInfo getOrder(UUID orderId) {
        OrderClient.OrderResponse resp = orderClient.getOrder(orderId);
        return new OrderInfo(resp.orderId(), resp.userId(), resp.couponId());
    }
}
```

### Kafka Consumer Adapter (Inbound)

```java
// FirstJoinCouponKafkaConsumer
@Component
public class FirstJoinCouponKafkaConsumer {
    private final IssueCouponUseCase issueCouponUseCase; // UseCase 주입 (Service 직접 아님)

    @KafkaListener(topics = "first-join-coupon", ...)
    public void onFirstJoin(FirstJoinCouponEvent msg) {
        issueCouponUseCase.issueFirstJoinCoupon(msg.userId());
    }
}

// OrderFailedKafkaConsumer
@Component
public class OrderFailedKafkaConsumer {
    private final RestoreCouponUseCase restoreCouponUseCase;

    @KafkaListener(topics = "order-failed", ...)
    public void onOrderFailed(OrderFailedEvent msg) {
        restoreCouponUseCase.restoreCouponByOrderFailed(msg.orderId());
    }
}
```

---

## Application Service 설계

### IssueCouponService

```java
@Service
@RequiredArgsConstructor
public class IssueCouponService implements IssueCouponUseCase {
    private final CouponPolicyRepositoryPort couponPolicyRepositoryPort;
    private final IssuedCouponRepositoryPort issuedCouponRepositoryPort;

    @Transactional
    public IssuedCouponResult issueCoupon(IssueCouponCommand command) {
        CouponPolicy policy = couponPolicyRepositoryPort.findActiveByCode(command.couponCode())
            .orElseThrow(() -> IssuedCouponException.couponPolicyNotFound());
        IssuedCoupon issued = IssuedCoupon.issue(command.userId(), policy.getCode(), policy.getEndAt());
        IssuedCoupon saved = issuedCouponRepositoryPort.save(issued);
        return IssuedCouponResult.from(saved);
    }

    @Transactional
    public IssuedCouponResult issueFirstJoinCoupon(UUID userId) {
        // FIRST_COUPON 정책 조회 또는 생성 후 발급
        ...
    }
}
```

### RestoreCouponService

```java
@Service
@RequiredArgsConstructor
public class RestoreCouponService implements RestoreCouponUseCase {
    private final IssuedCouponRepositoryPort issuedCouponRepositoryPort;
    private final OrderQueryPort orderQueryPort;  // Feign → Port 주입

    @Transactional
    public void restoreCouponByOrderFailed(UUID orderId) {
        OrderQueryPort.OrderInfo order = orderQueryPort.getOrder(orderId);
        if (order.couponId() == null) return;
        issuedCouponRepositoryPort.findByIdAndUserId(order.couponId(), order.userId())
            .filter(IssuedCoupon::isUsed)
            .ifPresent(coupon -> {
                coupon.restoreCoupon();
                issuedCouponRepositoryPort.save(coupon);
            });
    }
}
```

---

## 레거시 호환 전략

| 기존 클래스 | 처리 방식 |
|------------|-----------|
| `CouponPolicyService` | `@Deprecated(forRemoval = true)` 처리, `CouponPolicyRepositoryPort` 주입으로 변경 |
| `IssuedCouponService` | `@Deprecated(forRemoval = true)` 처리, `IssuedCouponRepositoryPort` + `OrderQueryPort` 주입으로 변경 |
| `domain/repository/CouponPolicyRepository` | `CouponPolicyRepositoryPort` 인터페이스로 대체 예정, 현재는 `adapter/out/persistence/CouponPolicyJpaRepository`로 이전 |
| `domain/repository/IssuedCouponRepository` | 동일 |
| `application/port/IssueFirstJoinCouponPort` | `domain/port/out/CouponEventPublisher`로 통합, 기존 파일 `@Deprecated` |
| `application/port/PublishCouponUsedEventPort` | `domain/port/out/CouponEventPublisher`로 통합 |

---

## 구현 순서 (Do Phase 체크리스트)

### Step 1: 도메인 모델 + VO (기반)
- [ ] `domain/model/vo/CouponCode.java` 신규
- [ ] `domain/model/vo/CouponPeriod.java` 신규
- [ ] `domain/model/CouponPolicy.java` 수정 (JPA 제거, presentation 의존 제거)
- [ ] `domain/model/IssuedCoupon.java` 수정 (JPA 제거, RequestUserDetails/Request 의존 제거)
- [ ] `domain/exception/CouponDomainException.java` 신규

### Step 2: Port 인터페이스
- [ ] `domain/port/in/IssueCouponUseCase.java`
- [ ] `domain/port/in/UseCouponUseCase.java`
- [ ] `domain/port/in/RestoreCouponUseCase.java`
- [ ] `domain/port/in/ManageCouponPolicyUseCase.java`
- [ ] `domain/port/out/CouponPolicyRepositoryPort.java`
- [ ] `domain/port/out/IssuedCouponRepositoryPort.java`
- [ ] `domain/port/out/OrderQueryPort.java`
- [ ] `domain/port/out/CouponEventPublisher.java`

### Step 3: Persistence Adapter
- [ ] `adapter/out/persistence/BaseJpaEntity.java`
- [ ] `adapter/out/persistence/CouponPolicyJpaEntity.java`
- [ ] `adapter/out/persistence/IssuedCouponJpaEntity.java`
- [ ] `adapter/out/persistence/CouponPolicyJpaRepository.java`
- [ ] `adapter/out/persistence/IssuedCouponJpaRepository.java`
- [ ] `adapter/out/persistence/CouponPolicyMapper.java`
- [ ] `adapter/out/persistence/IssuedCouponMapper.java`
- [ ] `adapter/out/persistence/CouponPolicyPersistenceAdapter.java`
- [ ] `adapter/out/persistence/IssuedCouponPersistenceAdapter.java`

### Step 4: Messaging + Client Adapter
- [ ] `adapter/out/messaging/KafkaCouponEventPublisher.java`
- [ ] `adapter/out/client/OrderFeignAdapter.java`

### Step 5: Application Service
- [ ] `application/dto/command/IssueCouponCommand.java`
- [ ] `application/dto/command/UseCouponCommand.java`
- [ ] `application/dto/command/CreateCouponPolicyCommand.java`
- [ ] `application/dto/result/IssuedCouponResult.java`
- [ ] `application/dto/result/CouponPolicyResult.java`
- [ ] `application/service/IssueCouponService.java`
- [ ] `application/service/UseCouponService.java`
- [ ] `application/service/RestoreCouponService.java`
- [ ] `application/service/ManageCouponPolicyService.java`

### Step 6: Kafka Consumer Adapter (Inbound) + 레거시 @Deprecated
- [ ] `adapter/in/kafka/FirstJoinCouponKafkaConsumer.java`
- [ ] `adapter/in/kafka/CouponUsedKafkaConsumer.java`
- [ ] `adapter/in/kafka/OrderFailedKafkaConsumer.java`
- [ ] `IssuedCouponService.java` → `@Deprecated` + Port 주입으로 교체
- [ ] `CouponPolicyService.java` → `@Deprecated` + Port 주입으로 교체

---

## 완료 기준

- `CouponPolicy.java`에 `import jakarta.persistence.*` 없음
- `IssuedCoupon.java`에 `import jakarta.persistence.*`, `RequestUserDetails`, `IssuedCouponRequest` 없음
- Port가 `domain/port/in/`, `domain/port/out/`에 위치
- Application Service가 Repository 직접 아닌 Port만 주입
- `infrastructure/kafka/consumer/` Kafka Consumer가 UseCase 주입 (Service 직접 아님)
- Gap Analysis Match Rate ≥ 90%
