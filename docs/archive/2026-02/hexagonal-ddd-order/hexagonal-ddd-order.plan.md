# Plan: Order 서비스 헥사고날 아키텍처 + DDD 완성

## 개요

- **Feature ID**: hexagonal-ddd-order
- **대상 서비스**: `order/`
- **작성일**: 2026-02-15
- **우선순위**: High
- **참조 패턴**: User (94.4%), Payment (94.1%), Coupon (92.1%) — 검증된 패턴 재사용
- **현재 완성도**: ~50% (Port/Adapter 구조 부분 적용, 레거시 혼재)

---

## 현재 상태 (As-Is)

Order 서비스는 헥사고날 마이그레이션이 **부분적으로 진행**된 혼재 상태이다.
도메인 모델(`Order.java`)은 이미 순수 Java이나, 레거시 `kafkaOrder/` 폴더와
`application/service/`에 미완성 서비스들이 공존하고 있다.

### 완료된 항목 (Done)

| 항목 | 파일 | 상태 |
|------|------|------|
| 순수 도메인 모델 | `domain/model/Order.java` | ✅ JPA 없음 |
| 도메인 VO | `domain/model/vo/Money.java`, `OrderQuantity.java`, `DiscountPolicy.java` | ✅ |
| UseCase 인터페이스 3개 | `domain/port/in/{Create,HandlePaymentSuccess,HandlePaymentFailure}UseCase.java` | ✅ |
| Outbound Port 5개 | `domain/port/out/{Broadcast,Product,Coupon,OrderRepository}QueryPort.java`, `OrderEventPublisher.java` | ✅ |
| Persistence Adapter | `adapter/out/persistence/{OrderJpaEntity,OrderJpaRepository,OrderMapper,OrderPersistenceAdapter}.java` | ✅ |
| Feign Adapter 3개 | `adapter/out/client/{Broadcast,Product,Coupon}FeignAdapter.java` | ✅ |
| Kafka Outbound Adapter | `adapter/out/messaging/KafkaOrderEventPublisher.java` | ✅ |
| Kafka Inbound Adapter | `adapter/in/kafka/PaymentKafkaConsumer.java` | ✅ |
| Payment 이벤트 서비스 | `application/service/{HandlePaymentSuccess,HandlePaymentFailure}Service.java` | ✅ |
| Command/Result DTO | `application/dto/command/CreateOrderCommand.java`, `application/dto/result/` | ✅ |
| Outbox 패턴 | `infrastructure/outbox/OutboxEventHelper.java` | ✅ |

### 미완료 항목 — 레거시 혼재 (Gap)

| 항목 | 파일 | 문제 |
|------|------|------|
| 레거시 Kafka 폴더 | `kafkaOrder/PaymentEventConsumer.java` | adapter/in/kafka 로 이전 필요 |
| 레거시 Kafka 폴더 | `kafkaOrder/product/InventoryEventProducer.java` | adapter/out/messaging 으로 이전 필요 |
| 레거시 Kafka 폴더 | `kafkaOrder/coupon/CouponUsedProducer.java` | adapter/out/messaging 으로 이전 필요 |
| 레거시 컨트롤러 | `kafkaOrder/controller/OrderControllerKafka.java` | presentation/controller 로 통합 필요 |
| 미마이그레이션 서비스 | `application/service/OrderService.java` | Port 없이 직접 repository 주입 |
| 미마이그레이션 서비스 | `application/service/OrderModificationService.java` | Port 없이 직접 repository 주입 |
| 미마이그레이션 서비스 | `application/service/PaymentStatusTransitionService.java` | Port 없이 직접 repository 주입 |
| 레거시 도메인 리포지토리 | `domain/repository/OrderRepository.java` | 삭제 또는 @Deprecated 처리 |
| 레거시 인프라 리포지토리 | `infrastructure/repository/OrderQueryRepository.java` | adapter/out/persistence 로 이전 |
| 미추상화 Feign 클라이언트 | `infrastructure/client/feign/{Inventory,Payment}Client.java` | Port+Adapter 패턴 미적용 |
| 누락 UseCase 인터페이스 | 없음 | GetOrder, GetOrders, UpdateOrder, DeleteOrder UseCase 필요 |
| 누락 Outbound Port | 없음 | InventoryPort, PaymentPort 필요 |

---

## 목표 (To-Be)

레거시 혼재 상태를 완전 정리하여 User/Payment/Coupon 서비스와 동일한 헥사고날 구조를 달성한다.

### 목표 패키지 구조

```
order/src/main/java/com/live_commerce/order/
├── domain/
│   ├── model/
│   │   ├── Order.java              ✅ 이미 완료
│   │   ├── OrderStatus.java        ✅
│   │   ├── DISCOUNT_TYPE.java      ✅
│   │   └── vo/                     ✅
│   ├── port/
│   │   ├── in/                     # UseCase 인터페이스
│   │   │   ├── CreateOrderUseCase.java          ✅
│   │   │   ├── HandlePaymentSuccessUseCase.java  ✅
│   │   │   ├── HandlePaymentFailureUseCase.java  ✅
│   │   │   ├── GetOrderUseCase.java              [신규]
│   │   │   ├── GetOrderListUseCase.java          [신규]
│   │   │   ├── UpdateOrderUseCase.java           [신규]
│   │   │   └── DeleteOrderUseCase.java           [신규]
│   │   └── out/                    # Outbound Port 인터페이스
│   │       ├── OrderRepositoryPort.java          ✅
│   │       ├── OrderEventPublisher.java          ✅
│   │       ├── BroadcastQueryPort.java           ✅
│   │       ├── ProductQueryPort.java             ✅
│   │       ├── CouponQueryPort.java              ✅
│   │       ├── InventoryPort.java                [신규]
│   │       └── PaymentPort.java                  [신규]
│   └── exception/
│       └── OrderDomainException.java             ✅
├── application/
│   ├── service/
│   │   ├── CreateOrderService.java  [리팩토링: port 사용으로 완성]
│   │   ├── HandlePaymentSuccessService.java      ✅
│   │   ├── HandlePaymentFailureService.java      ✅
│   │   ├── GetOrderService.java     [신규 또는 OrderService 리팩토링]
│   │   ├── UpdateOrderService.java  [OrderModificationService 리팩토링]
│   │   └── DeleteOrderService.java  [OrderService 일부 추출]
│   └── dto/
│       ├── command/                  ✅ CreateOrderCommand
│       └── result/                   ✅ CreateOrderResult
├── adapter/
│   ├── in/
│   │   ├── web/
│   │   │   └── OrderController.java  [presentation → adapter/in/web 이동]
│   │   └── kafka/
│   │       ├── PaymentKafkaConsumer.java          ✅
│   │       └── InventoryKafkaConsumer.java        [kafkaOrder에서 이전]
│   └── out/
│       ├── persistence/              ✅ 이미 완료
│       ├── client/                   ✅ Broadcast/Product/Coupon FeignAdapter
│       │   ├── InventoryFeignAdapter.java         [신규: InventoryPort 구현]
│       │   └── PaymentFeignAdapter.java           [신규: PaymentPort 구현]
│       └── messaging/
│           ├── KafkaOrderEventPublisher.java      ✅
│           ├── KafkaCouponEventPublisher.java     [kafkaOrder에서 이전]
│           └── KafkaInventoryEventPublisher.java  [kafkaOrder에서 이전]
└── presentation/
    └── controller/                   [레거시 유지 또는 adapter/in/web으로 통합]
```

---

## 단계별 구현 계획

### Step 1: 누락 UseCase 인터페이스 추가 (domain/port/in/)

```java
// GetOrderUseCase.java
public interface GetOrderUseCase {
    OrderGetResult getOrder(UUID orderId, RequestUserDetails userDetails);
}

// GetOrderListUseCase.java
public interface GetOrderListUseCase {
    List<OrderGetResult> getOrders(UUID userId, Pageable pageable, RequestUserDetails userDetails);
}

// UpdateOrderUseCase.java
public interface UpdateOrderUseCase {
    OrderUpdateResult updateOrder(UUID orderId, UpdateOrderCommand command, RequestUserDetails userDetails);
}

// DeleteOrderUseCase.java
public interface DeleteOrderUseCase {
    void deleteOrder(UUID orderId, RequestUserDetails userDetails);
}
```

### Step 2: 누락 Outbound Port 추가 (domain/port/out/)

```java
// InventoryPort.java
public interface InventoryPort {
    void decreaseInventory(UUID productId, int quantity);
    void increaseInventory(UUID productId, int quantity);  // 보상 트랜잭션용
    boolean checkOrderableInventory(UUID productId, int quantity);
}

// PaymentPort.java
public interface PaymentPort {
    PaymentReadyResult readyPayment(UUID orderId, UUID userId, int amount);
    void notifyPaymentSuccess(UUID orderId, String pgToken);
    void notifyPaymentFailure(UUID orderId, String reason);
    void refundPayment(UUID orderId, String reason);
}
```

### Step 3: 레거시 kafkaOrder/ → adapter/ 이전

**kafkaOrder/product/InventoryEventProducer.java** → `adapter/out/messaging/KafkaInventoryEventPublisher.java`
- `OrderEventPublisher` 인터페이스 구현으로 전환

**kafkaOrder/coupon/CouponUsedProducer.java** → `adapter/out/messaging/KafkaCouponEventPublisher.java`
- 새로운 `CouponUsedEventPort` 인터페이스 구현

**kafkaOrder/payment/PaymentEventConsumer.java** → `adapter/in/kafka/` 통합
- 기존 `PaymentKafkaConsumer`에 통합하거나 별도 클래스로 유지

**kafkaOrder/controller/OrderControllerKafka.java** → `presentation/controller/` 통합
- 중복 엔드포인트 제거, `OrderController`와 통합

### Step 4: 미마이그레이션 서비스 리팩토링

**OrderService.java** (현재 직접 repository 주입):
- CRUD 기능 → `GetOrderService`, `DeleteOrderService`로 분리
- Port 인터페이스(`OrderRepositoryPort`) 사용으로 전환

**OrderModificationService.java** → **UpdateOrderService.java**:
- `UpdateOrderUseCase` 구현
- `OrderRepositoryPort` 사용

**PaymentStatusTransitionService.java**:
- `PaymentPort`와 연동하도록 리팩토링
- 결제 상태 전환 로직 순수 도메인 메서드로 이동 검토

### Step 5: InventoryFeignAdapter + PaymentFeignAdapter 추가

```java
// InventoryFeignAdapter.java (adapter/out/client/)
@Component
@RequiredArgsConstructor
public class InventoryFeignAdapter implements InventoryPort {
    private final InventoryClient inventoryClient;

    @Override
    public void decreaseInventory(UUID productId, int quantity) {
        inventoryClient.decrease(new InventoryDecreaseRequestDto(productId, quantity));
    }

    @Override
    public boolean checkOrderableInventory(UUID productId, int quantity) {
        return inventoryClient.checkOrderable(productId, quantity).isOrderable();
    }
}

// PaymentFeignAdapter.java (adapter/out/client/)
@Component
@RequiredArgsConstructor
public class PaymentFeignAdapter implements PaymentPort {
    private final PaymentClient paymentClient;
    // ...
}
```

### Step 6: 레거시 정리 및 @Deprecated 처리

| 파일 | 처리 방법 |
|------|----------|
| `kafkaOrder/` 전체 | 이전 완료 후 삭제 또는 `@Deprecated` |
| `domain/repository/OrderRepository.java` | 삭제 (`adapter/out/persistence/OrderPersistenceAdapter`로 대체됨) |
| `infrastructure/repository/OrderQueryRepository.java` | `adapter/out/persistence/` 로 이전 후 삭제 |
| `infrastructure/client/feign/InventoryClient.java` | `InventoryFeignAdapter` 완성 후 직접 노출 제거 |
| `infrastructure/client/feign/PaymentClient.java` | `PaymentFeignAdapter` 완성 후 직접 노출 제거 |
| `application/service/OrderService.java` | 리팩토링 완료 후 `@Deprecated` 처리 |
| `application/service/OrderModificationService.java` | `@Deprecated` |
| `application/service/PaymentStatusTransitionService.java` | `@Deprecated` |

---

## 검증 기준 (Verification Criteria)

| 검증 항목 | 기대값 |
|----------|--------|
| `Order.java`에 `import jakarta.persistence.*` 없음 | 없음 (이미 달성) |
| `kafkaOrder/` 폴더 삭제 | 삭제됨 |
| `domain/repository/` 삭제 | 삭제됨 |
| `domain/port/in/` UseCase 7개 | 존재 |
| `domain/port/out/` Port 7개 | 존재 |
| `InventoryPort`, `PaymentPort` | adapter/out/client/ 에 구현체 존재 |
| OrderController가 UseCase 인터페이스만 주입 | repository 직접 주입 없음 |
| 빌드 성공 | 성공 |

---

## 우선순위 및 난이도

| 항목 | 우선순위 | 복잡도 |
|------|---------|--------|
| kafkaOrder/ 이전 | High | Low (단순 이동) |
| UseCase 인터페이스 추가 | High | Low |
| InventoryPort + PaymentPort | High | Medium |
| OrderService 리팩토링 | High | Medium |
| OrderModificationService 리팩토링 | Medium | Medium |
| PaymentStatusTransitionService 정리 | Medium | High (Saga 연계) |

**예상 작업량**: 2-3일

---

## 참조 문서

- 완료된 패턴: `docs/02-design/features/hexagonal-ddd-user.design.md`
- 기존 구현: `order/src/main/java/com/live_commerce/order/adapter/`
- 전체 마이그레이션 전략: `docs/01-plan/features/hexagonal-ddd-domain-services.plan.md`

---

## 전체 마이그레이션 로드맵 (참조)

| 순서 | 서비스 | 완성도 | 복잡도 | 예상 소요 |
|-----|--------|--------|--------|---------|
| ✅ coupon | hexagonal-ddd-coupon | 92.1% | Medium | 완료 |
| ✅ payment | hexagonal-ddd-payment | 94.1% | High | 완료 |
| ✅ user | hexagonal-ddd-user | 94.4% | High | 완료 |
| **→ 1** | **order** | 50% | High | 2-3일 |
| → 2 | product | 0% | Very High | 3-4일 |
| → 3 | livebroadcast | 0% | Medium | 2-3일 |
| → 4 | chat | 0% | Medium | 1-2일 |
| → 5 | notification | 0% | Low | 1일 |
| → 6 | company | 0% | Very Low | 0.5일 |
| → 7 | ai | 0% | Very Low | 0.5일 |

---

## 버전 이력

| 버전 | 날짜 | 변경 내용 | 작성자 |
|------|------|---------|--------|
| 1.0 | 2026-02-15 | 최초 작성 | product-manager |
