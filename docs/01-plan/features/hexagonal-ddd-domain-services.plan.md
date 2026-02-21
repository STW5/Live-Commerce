# Plan: 도메인 서비스 헥사고날 아키텍처 + DDD 확장 적용

## 개요

Order 서비스에서 검증된 Hexagonal Architecture + DDD 패턴을 User, Coupon, LiveBroadcast, Product 서비스로 확장 적용한다.

- **Feature ID**: hexagonal-ddd-domain-services
- **작성일**: 2026-02-14
- **우선순위**: High
- **예상 범위**: 4개 서비스 × 점진적 마이그레이션

---

## 현재 상태 (As-Is)

### 공통 문제점

| 서비스 | 도메인 모델 | Port 유무 | Adapter 유무 | Repository 위치 |
|--------|------------|-----------|-------------|----------------|
| user | @Entity 직접 포함 | 없음 | 없음 | domain/repository + infrastructure/repository |
| coupon | @Entity 직접 포함 | **일부 있음** (application/port) | 없음 | domain/repository |
| livebroadcast | @Entity 직접 포함 | 없음 | 없음 | domain/repository + infrastructure/repository |
| product | @Entity 직접 포함 | 없음 | 없음 | domain/repository + infrastructure/repository |

### 서비스별 상세 현황

**User 서비스**
- `User.java`: `@Entity`, `@Table(name = "p_user")` 직접 포함
- `AuthService`, `UserService`: `UserRepository` (JPA) 직접 주입
- Port 없음, Adapter 없음
- Kafka Producer: `infrastructure/kafka/producer/`

**Coupon 서비스**
- `CouponPolicy.java`, `IssuedCoupon.java`: `@Entity` 직접 포함
- **기존 Port**: `IssueFirstJoinCouponPort`, `PublishCouponUsedEventPort` (application/port/)
- Port 인터페이스 방향 혼재 (application layer에 위치)
- `domain/service/` 존재 (도메인 서비스 레이어 시도됨)

**LiveBroadcast 서비스**
- `LiveBroadcast.java`, `BroadcastProduct.java`, `BroadcastSubscription.java`: `@Entity` 직접 포함
- `domain/repository/` + `infrastructure/repository/` 레이어 혼재
- QueryDSL: `domain/repository/query/` (도메인 레이어에 위치)
- Feign: Company, Notification, Product 3개 외부 서비스 호출

**Product 서비스**
- `product/` + `inventory/` 두 서브 도메인 구조
- `Product.java`, `ProductDiscount.java`, `Inventory.java`: `@Entity` 직접 포함
- Redisson 분산락 (`@DistributedLock`) 사용 중 → Adapter 설계 주의 필요
- `domain/service/` 존재 (일부 도메인 로직 분리 시도됨)

---

## 목표 (To-Be)

Order 서비스에서 검증된 패턴을 동일하게 적용:

```
도메인 모델 (순수 Java)
    ↕ Port 인터페이스
Application Service (UseCase 구현)
    ↕ Adapter (JPA / Feign / Kafka / Redis)
```

### 공통 목표 구조

```
{service}/src/main/java/com/live_commerce/{service}/
├── domain/
│   ├── model/           # 순수 Java (JPA 없음), VO 포함
│   ├── model/vo/        # Value Objects
│   ├── port/in/         # UseCase 인터페이스
│   ├── port/out/        # Repository/Event Port 인터페이스
│   └── exception/       # 도메인 예외
├── application/
│   ├── service/         # UseCase 구현체
│   └── dto/command|result/  # 커맨드/결과 DTO
├── adapter/
│   ├── in/              # Web Controller Adapter (선택), Kafka Consumer
│   └── out/
│       ├── persistence/ # JpaEntity + Repository + Mapper + Adapter
│       ├── messaging/   # Kafka Publisher
│       └── client/      # Feign Adapter (해당 시)
└── ...
```

---

## 적용 우선순위 및 범위

### Phase 1: Coupon 서비스 (최우선)

**이유**: 이미 `application/port/` 일부 존재, 도메인 단순, Order 서비스와 직접 연동

**작업 범위**:
- `CouponPolicy`, `IssuedCoupon` → 순수 도메인 객체 + JpaEntity 분리
- VO 추출: `CouponCode`, `DiscountValue`, `CouponPeriod`
- Port 재배치: `application/port/` → `domain/port/in/`, `domain/port/out/`
- Adapter: `CouponPersistenceAdapter`, `KafkaCouponEventPublisher`
- UseCase: `IssueCouponUseCase`, `ValidateCouponUseCase`, `UseCouponUseCase`

### Phase 2: User 서비스

**이유**: 인증/인가 핵심 서비스, 도메인 모델 상대적으로 단순

**작업 범위**:
- `User` → 순수 도메인 객체 + UserJpaEntity 분리
- VO 추출: `Email`, `Username`, `UserRole`
- Port: `UserRepositoryPort`, `PasswordEncoderPort`, `JwtTokenPort`, `UserEventPublisher`
- UseCase: `RegisterUserUseCase`, `AuthenticateUserUseCase`, `GetUserUseCase`
- Kafka Adapter: `UserKafkaEventPublisher`

### Phase 3: LiveBroadcast 서비스

**이유**: Order 서비스가 `BroadcastQueryPort`로 직접 호출, 방송 생명주기 명확

**작업 범위**:
- `LiveBroadcast`, `BroadcastProduct`, `BroadcastSubscription` → 순수 도메인 객체 분리
- VO 추출: `BroadcastStatus`, `ViewerCount`
- Port: `LiveBroadcastRepositoryPort`, `BroadcastStatusQueryPort`, `BroadcastEventPublisher`
- Feign Adapter: CompanyFeignAdapter, NotificationFeignAdapter, ProductFeignAdapter
- QueryDSL: `domain/repository/query/` → `adapter/out/persistence/` 이동
- UseCase: `StartBroadcastUseCase`, `EndBroadcastUseCase`, `GetBroadcastStatusUseCase`

### Phase 4: Product 서비스 (최후)

**이유**: product + inventory 두 서브 도메인, Redisson 분산락 복잡도

**작업 범위**:
- `Product`, `ProductDiscount` → 순수 도메인 객체 분리
- `Inventory` → 순수 도메인 객체 분리 (Redisson Lock은 Adapter에서 처리)
- VO 추출: `StockQuantity`, `ProductPrice`, `DiscountRate`
- Port: `ProductRepositoryPort`, `InventoryRepositoryPort`, `DistributedLockPort`, `InventoryEventPublisher`
- UseCase: `GetProductUseCase`, `DecreaseInventoryUseCase`, `RollbackInventoryUseCase`

---

## 마이그레이션 전략

Order 서비스에서 검증된 **점진적 마이그레이션** 전략 동일 적용:

1. 기존 코드 동작 유지 (`@Deprecated` 처리)
2. 신규 구조를 옆에 추가
3. 기존 코드가 새 Port/Adapter를 통해 동작하도록 전환
4. 안정화 후 레거시 코드 제거 (별도 커밋)

---

## PDCA 사이클 계획

| Phase | Feature Name | 서비스 | 예상 Match Rate |
|-------|-------------|--------|---------------|
| 1 | hexagonal-ddd-coupon | coupon | ≥ 90% |
| 2 | hexagonal-ddd-user | user | ≥ 90% |
| 3 | hexagonal-ddd-livebroadcast | livebroadcast | ≥ 90% |
| 4 | hexagonal-ddd-product | product | ≥ 90% |

각 Phase는 독립적인 PDCA 사이클로 관리: Plan → Design → Do → Analyze → (Iterate) → Report

---

## 제약사항 및 리스크

| 리스크 | 내용 | 대응 |
|--------|------|------|
| 테스트 깨짐 | 기존 테스트가 @Entity 도메인 객체 직접 사용 | reconstitute() 팩토리 활용, 기존 테스트 최소 수정 |
| Redisson Lock 복잡성 | Product/Inventory의 @DistributedLock AOP | DistributedLockPort 인터페이스로 추상화 |
| 쿠폰 기존 Port 방향 | application/port가 아닌 domain/port로 재배치 필요 | 기존 구현체 유지, 인터페이스만 이동 |
| QueryDSL Q-class | @Entity 분리 시 QClass 재생성 필요 | Order 서비스 패턴 동일 적용 (JpaEntity 기반 Q-class) |

---

## 완료 기준

- 각 서비스 도메인 모델에 `import jakarta.persistence.*` 없음
- Port 인터페이스가 `domain/port/in/`, `domain/port/out/` 위치
- Application Service가 Port 인터페이스만 주입 (구현체 직접 의존 없음)
- 기존 동작 유지 (서비스 시작 시 에러 없음)
- Gap Analysis Match Rate ≥ 90%
