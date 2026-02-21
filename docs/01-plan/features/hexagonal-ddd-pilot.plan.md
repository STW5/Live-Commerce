# Plan: Hexagonal Architecture + DDD Pilot (Order Service)

## 메타
- **Feature**: hexagonal-ddd-pilot
- **Phase**: Plan
- **생성일**: 2026-02-13
- **대상 서비스**: Order Service (파일럿)
- **적용 범위**: 핵심 서비스 1개 파일럿 → 검증 후 전파

---

## 배경 및 문제 분석

### 현재 Order 서비스의 문제점

```
현재 구조 (문제 있는 의존성):
┌─────────────────────────────────────────────────┐
│ presentation (Controller, BaseEntity!)          │
│         ↓ (domain이 presentation에 의존!)       │
│ application (Service, DTO)                      │
│         ↓ (application이 infra에 직접 의존!)    │
│ infrastructure (FeignClient, Kafka, JPA)        │
│         ↓                                       │
│ domain (Order - JPA @Entity 포함)               │
└─────────────────────────────────────────────────┘
```

**구체적인 위반 사항:**

| # | 파일 | 위반 내용 | 심각도 |
|---|------|----------|--------|
| 1 | `Order.java` | `presentation.common.BaseEntity` 상속 → domain이 presentation에 의존 | 🔴 Critical |
| 2 | `Order.java` | `@Entity`, `@Table` 등 JPA 어노테이션 직접 사용 → domain이 infra에 의존 | 🔴 Critical |
| 3 | `Order.java` | `application.exception.OrderException` import → domain이 application에 의존 | 🟠 High |
| 4 | `OrderCreateService` | `BroadcastClient`, `CouponClient`, `ProductClient` (Feign) 직접 의존 | 🟠 High |
| 5 | `OrderCreateService` | 쿠폰 할인 계산 로직이 Application Service에 인라인 → 도메인 로직 유출 | 🟡 Medium |
| 6 | `Order.java` | `Money`/`Quantity` 타입 없이 `Double`/`Integer` 사용 → 원시 타입 집착 | 🟡 Medium |
| 7 | `kafkaOrder/` 패키지 | `application/` 하위가 아닌 최상위 패키지 → 계층 구조 모호 | 🟡 Medium |

---

## 목표

### 핵사고날 아키텍처 적용 후 기대 구조

```
                    ┌─────────────────────────────┐
                    │  Driving Adapters (좌)        │
                    │  - REST Controller (HTTP)     │
                    │  - Kafka Consumer (Event)     │
                    └──────────┬──────────────────┘
                               │ 호출 (Port 통해)
                    ┌──────────▼──────────────────┐
                    │     Application Layer        │
                    │  Use Cases / Port Interfaces │
                    │  (InboundPort, OutboundPort) │
                    └──────────┬──────────────────┘
                               │ 호출
                    ┌──────────▼──────────────────┐
                    │       Domain Layer           │
                    │  Order Aggregate             │
                    │  Value Objects (Money, etc.) │
                    │  Domain Service              │
                    │  (순수 Java, NO 프레임워크)   │
                    └──────────────────────────────┘
                               ▲ 구현 (의존성 역전)
                    ┌──────────┴──────────────────┐
                    │  Driven Adapters (우)         │
                    │  - JPA Adapter (Persistence)  │
                    │  - Feign Adapter (외부 서비스) │
                    │  - Kafka Producer (Event)     │
                    └─────────────────────────────┘
```

### 목표 패키지 구조

```
order/src/main/java/com/live_commerce/order/
├── domain/                          ← 순수 도메인 (외부 의존 ZERO)
│   ├── model/
│   │   ├── Order.java               ← Aggregate Root (JPA 제거)
│   │   ├── OrderItem.java           ← Entity
│   │   ├── OrderStatus.java         ← Enum
│   │   └── vo/
│   │       ├── Money.java           ← Value Object
│   │       ├── OrderQuantity.java   ← Value Object
│   │       └── CouponDiscount.java  ← Value Object (할인 계산 로직)
│   ├── event/
│   │   └── OrderCreatedEvent.java   ← Domain Event (순수 record)
│   ├── exception/
│   │   └── OrderDomainException.java ← Domain Exception
│   └── port/
│       ├── in/                      ← Inbound Ports (Use Cases)
│       │   ├── CreateOrderUseCase.java
│       │   └── UpdateOrderStatusUseCase.java
│       └── out/                     ← Outbound Ports (외부 의존성 추상화)
│           ├── OrderRepository.java  ← 영속성 포트
│           ├── BroadcastQueryPort.java
│           ├── ProductQueryPort.java
│           └── CouponQueryPort.java
│
├── application/                     ← Use Case 구현, 오케스트레이션만
│   ├── service/
│   │   ├── CreateOrderService.java  ← CreateOrderUseCase 구현
│   │   └── UpdateOrderStatusService.java
│   └── dto/
│       ├── command/                 ← 입력 Command
│       │   └── CreateOrderCommand.java
│       └── result/                  ← 출력 Result
│           └── CreateOrderResult.java
│
├── adapter/                         ← 어댑터 (infra → adapter 이름 변경)
│   ├── in/
│   │   ├── web/                     ← REST API Driving Adapter
│   │   │   └── OrderController.java
│   │   └── kafka/                   ← Kafka Consumer Driving Adapter
│   │       └── PaymentEventConsumer.java
│   └── out/
│       ├── persistence/             ← JPA Driven Adapter
│       │   ├── OrderJpaEntity.java  ← @Entity (인프라 전용)
│       │   ├── OrderJpaRepository.java
│       │   └── OrderPersistenceAdapter.java ← OrderRepository 구현
│       ├── client/                  ← Feign Driven Adapter
│       │   ├── BroadcastFeignAdapter.java  ← BroadcastQueryPort 구현
│       │   ├── ProductFeignAdapter.java
│       │   └── CouponFeignAdapter.java
│       └── messaging/               ← Kafka Producer Driven Adapter
│           └── OrderEventPublisher.java
│
└── config/                          ← Spring 설정 (Security, Feign, etc.)
```

---

## 핵심 설계 결정

### 1. Value Object 도입

**현재 (문제):**
```java
private Double productTotalPrice;  // Double이 뭘 의미하는지 불명확
private Integer productQuantity;    // 음수도 허용됨
```

**목표:**
```java
// Money.java - 불변, 유효성 검증 포함
public record Money(BigDecimal amount) {
    public Money {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0)
            throw new OrderDomainException("금액은 0 이상이어야 합니다.");
    }
    public Money add(Money other) { return new Money(this.amount.add(other.amount)); }
    public Money subtract(Money other) { return new Money(this.amount.subtract(other.amount)); }
}

// CouponDiscount.java - 할인 계산 로직 캡슐화
public record CouponDiscount(DiscountType type, BigDecimal value) {
    public Money apply(Money totalPrice) {
        return switch (type) {
            case FIXED -> totalPrice.subtract(new Money(value));
            case RATE -> {
                BigDecimal discountAmount = totalPrice.amount()
                    .multiply(value).divide(BigDecimal.valueOf(100));
                yield totalPrice.subtract(new Money(discountAmount));
            }
        };
    }
}
```

### 2. Outbound Port 정의 (의존성 역전)

**현재 (문제):**
```java
// OrderCreateService가 Feign 구현체에 직접 의존
private final BroadcastClient broadcastClient;  // infrastructure 클래스
private final CouponClient couponClient;        // infrastructure 클래스
```

**목표 (Port & Adapter):**
```java
// domain/port/out/BroadcastQueryPort.java (domain에 정의)
public interface BroadcastQueryPort {
    BroadcastInfo findById(UUID broadcastId);
    boolean isLive(UUID broadcastId);
}

// adapter/out/client/BroadcastFeignAdapter.java (infrastructure에 구현)
@Component
public class BroadcastFeignAdapter implements BroadcastQueryPort {
    private final BroadcastClient broadcastClient; // Feign
    // ...구현
}
```

### 3. Domain Model 순수화

**현재 (문제):**
```java
@Entity  // JPA 어노테이션 → infra 의존
public class Order extends BaseEntity {  // presentation에 의존!
```

**목표:**
```java
// domain/model/Order.java - 순수 Java
public class Order {
    private OrderId id;
    private UserId userId;
    private ProductId productId;
    private OrderQuantity quantity;
    private Money totalPrice;
    private Money finalPrice;
    private OrderStatus status;
    // ...비즈니스 메서드만

    public static Order create(CreateOrderCommand cmd, ProductInfo product, CouponDiscount discount) {
        // 도메인 로직
    }
}

// adapter/out/persistence/OrderJpaEntity.java - JPA 전용
@Entity @Table(name = "p_order")
public class OrderJpaEntity extends BaseJpaEntity { ... }
```

---

## 구현 단계 계획

### Phase 1: Domain Layer 재설계 (핵심)
- [ ] `OrderDomainException` 이동 (domain/exception)
- [ ] Value Objects 생성: `Money`, `OrderQuantity`, `OrderId`, `CouponDiscount`
- [ ] `Order` Aggregate 순수화 (JPA 제거, 비즈니스 메서드 강화)
- [ ] Outbound Port 인터페이스 정의 (4개: OrderRepo, Broadcast, Product, Coupon)
- [ ] Inbound Port 인터페이스 정의 (UseCase 인터페이스)

### Phase 2: Application Layer 정리
- [ ] `CreateOrderService` → UseCase 인터페이스 구현체로 변환
- [ ] 도메인 로직(할인 계산)을 VO로 이전
- [ ] Command/Result DTO 분리

### Phase 3: Adapter Layer 구성
- [ ] Persistence Adapter (`OrderJpaEntity` + `OrderPersistenceAdapter`)
- [ ] Feign Adapters (`BroadcastFeignAdapter`, `ProductFeignAdapter`, `CouponFeignAdapter`)
- [ ] Kafka Adapters 재구성 (`adapter/in/kafka`, `adapter/out/messaging`)
- [ ] Web Adapter (`OrderController` 이동)

### Phase 4: 패키지 이름 정리
- [ ] `infrastructure/` → `adapter/`
- [ ] `kafkaOrder/` 해체 → `adapter/in/kafka`, `adapter/out/messaging`
- [ ] 기존 테스트 업데이트

---

## 성공 기준

| 기준 | 측정 방법 |
|------|----------|
| Domain Layer의 외부 의존성 = 0 | `domain/` 패키지에서 `@Entity`, Feign, Spring 없음 |
| Port 인터페이스로만 외부 통신 | Application Layer에서 Feign 클래스 import 없음 |
| Value Object 사용 | `Double`/`Integer` 원시타입 제거, Money/Quantity VO 사용 |
| 테스트 가능성 향상 | Domain/Application 테스트 시 Mock 없이 순수 단위 테스트 가능 |
| 컴파일 에러 없이 기존 기능 유지 | 전체 빌드 성공, 기존 API 동작 유지 |

---

## 리스크 및 대응

| 리스크 | 대응 방안 |
|--------|----------|
| 대규모 패키지 이동으로 컴파일 오류 급증 | Phase별 점진적 적용, 기존 클래스 유지하며 새 구조 추가 후 교체 |
| JPA Entity와 Domain Model 매핑 복잡성 | Mapper 클래스 도입 (`OrderMapper.java`) |
| Feign 어댑터 래핑 시 응답 DTO 이중화 | Feign 전용 DTO는 adapter 패키지에만 위치 |
| 기존 OutboxEvent 패턴과 충돌 | Outbox는 adapter/out/messaging 레이어에서 유지 |

---

## 참고 자료

- 적용 대상: `order/src/main/java/com/live_commerce/order/`
- 현재 도메인 파일: `domain/model/Order.java`, `domain/repository/OrderRepository.java`
- 현재 핵심 서비스: `application/service/OrderCreateService.java`
- 기존 아웃박스 가이드: `SAGA_OUTBOX_IMPLEMENTATION_GUIDE.md`
