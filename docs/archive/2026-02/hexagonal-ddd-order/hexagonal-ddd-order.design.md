# Design: Order 서비스 헥사고날 아키텍처 + DDD 완성

## 개요

- **Feature**: hexagonal-ddd-order
- **대상 서비스**: `order/`
- **작성일**: 2026-02-15
- **참조 Plan**: `docs/01-plan/features/hexagonal-ddd-order.plan.md`
- **참조 패턴**: User (94.4%), Payment (94.1%), Coupon (92.1%)

---

## 현재 상태 분석 (As-Is)

Order 서비스는 헥사고날 마이그레이션이 **부분 완료**된 혼재 상태이다.
`CreateOrderService`, `HandlePaymentSuccessService`, `HandlePaymentFailureService` 등 핵심 유즈케이스는
이미 Port/Adapter 패턴으로 구현되어 있으나, 레거시 `kafkaOrder/` 폴더와
직접 Feign 클라이언트를 주입하는 미완성 서비스들이 공존한다.

### 완료된 항목 (Done)

| 파일 | 설명 | 위치 |
|------|------|------|
| `domain/model/Order.java` | 순수 도메인 (JPA 없음), 팩토리 메서드 `create()`, `reconstitute()` | ✅ |
| `domain/model/vo/Money.java` | 불변 금액 VO | ✅ |
| `domain/model/vo/OrderQuantity.java` | 수량 VO (최소값 검증) | ✅ |
| `domain/model/vo/DiscountPolicy.java` | 할인 정책 VO (FIXED/RATE) | ✅ |
| `domain/port/in/CreateOrderUseCase` | 주문 생성 UseCase | ✅ |
| `domain/port/in/HandlePaymentSuccessUseCase` | 결제 성공 처리 UseCase | ✅ |
| `domain/port/in/HandlePaymentFailureUseCase` | 결제 실패 처리 UseCase | ✅ |
| `domain/port/out/OrderRepositoryPort` | 주문 영속성 Port | ✅ |
| `domain/port/out/OrderEventPublisher` | Kafka 이벤트 발행 Port | ✅ |
| `domain/port/out/BroadcastQueryPort` | 방송 상태 조회 Port | ✅ |
| `domain/port/out/ProductQueryPort` | 상품/재고 조회 Port | ✅ |
| `domain/port/out/CouponQueryPort` | 쿠폰 조회 Port | ✅ |
| `adapter/in/kafka/PaymentKafkaConsumer` | 결제 이벤트 Kafka Consumer (hexagonal) | ✅ |
| `adapter/out/client/BroadcastFeignAdapter` | BroadcastQueryPort 구현체 | ✅ |
| `adapter/out/client/ProductFeignAdapter` | ProductQueryPort 구현체 | ✅ |
| `adapter/out/client/CouponFeignAdapter` | CouponQueryPort 구현체 | ✅ |
| `adapter/out/messaging/KafkaOrderEventPublisher` | OrderEventPublisher 구현체 | ✅ |
| `adapter/out/persistence/*` | JPA Persistence Adapter (완전 분리) | ✅ |
| `application/service/CreateOrderService` | CreateOrderUseCase 구현, Port만 주입 | ✅ |
| `application/service/HandlePaymentSuccessService` | HandlePaymentSuccessUseCase 구현 | ✅ |
| `application/service/HandlePaymentFailureService` | HandlePaymentFailureUseCase 구현 | ✅ |

### 미완료 항목 — 레거시 혼재 (Gap)

| 파일 | 문제점 | 처리 방향 |
|------|--------|----------|
| `kafkaOrder/payment/PaymentEventConsumer.java` | `adapter/in/kafka/PaymentKafkaConsumer.java`와 중복 | 삭제 |
| `kafkaOrder/product/InventoryEventProducer.java` | Port 없이 직접 KafkaTemplate 사용 | adapter/out/messaging으로 이전 |
| `kafkaOrder/coupon/CouponUsedProducer.java` | Port 없이 직접 KafkaTemplate 사용 | adapter/out/messaging으로 이전 |
| `kafkaOrder/controller/OrderControllerKafka.java` | OrderController와 중복 엔드포인트 | 통합 후 삭제 |
| `kafkaOrder/service/OrderCreateServiceKafka.java` | CreateOrderService로 대체됨 | 삭제 |
| `kafkaOrder/service/OrderServiceKafka.java` | OrderService 래퍼, 레거시 | 삭제 |
| `kafkaOrder/service/PaymentSuccessServiceKafka.java` | HandlePaymentSuccessService와 중복 | 삭제 |
| `kafkaOrder/service/PaymentFailureServiceKafka.java` | HandlePaymentFailureService와 중복 | 삭제 |
| `kafkaOrder/service/PaymentStatusTransitionServiceKafka.java` | Port 없이 Feign 직접 사용 | 통합 후 삭제 |
| `application/service/OrderService.java` | ProductClient/PaymentClient/CouponClient 직접 주입 | GetOrder/Delete로 분리, Port 전환 |
| `application/service/OrderModificationService.java` | ProductClient/CouponClient 직접 주입 | UpdateOrderService로 리팩토링 |
| `application/service/PaymentStatusTransitionService.java` | PaymentClient/ProductClient 직접 주입 | Port 전환 |
| `application/service/OrderCreateService.java` | 레거시 OrderCreateService (kafkaOrder 방식) | 삭제 (CreateOrderService로 대체) |
| `presentation/controller/OrderController.java` | OrderService 직접 주입 (UseCase 인터페이스 아님) | UseCase 인터페이스로 전환 |
| `domain/repository/OrderRepository.java` | 도메인에 JPA Repository | 삭제 |
| `infrastructure/repository/OrderQueryRepository.java` | QueryDSL 인프라, 잘못된 레이어 위치 | adapter/out/persistence로 이전 |
| 누락: `GetOrderUseCase` 등 4개 | CRUD UseCase 인터페이스 없음 | domain/port/in/ 신규 추가 |
| 누락: `InventoryPort`, `PaymentPort` | 쓰기 작업 Port 없음 | domain/port/out/ 신규 추가 |
| 누락: `InventoryFeignAdapter`, `PaymentFeignAdapter` | 신규 Port 구현체 없음 | adapter/out/client/ 신규 추가 |

---

## 목표 패키지 구조 (To-Be)

```
order/src/main/java/com/live_commerce/order/
├── domain/
│   ├── model/
│   │   ├── Order.java                           ✅ 순수 도메인 (이미 완료)
│   │   ├── OrderStatus.java                     ✅
│   │   ├── DISCOUNT_TYPE.java                   ✅
│   │   └── vo/                                  ✅ Money, OrderQuantity, DiscountPolicy
│   ├── port/
│   │   ├── in/
│   │   │   ├── CreateOrderUseCase.java           ✅
│   │   │   ├── HandlePaymentSuccessUseCase.java  ✅
│   │   │   ├── HandlePaymentFailureUseCase.java  ✅
│   │   │   ├── GetOrderUseCase.java              [신규]
│   │   │   ├── GetOrderListUseCase.java          [신규]
│   │   │   ├── UpdateOrderUseCase.java           [신규]
│   │   │   └── DeleteOrderUseCase.java           [신규]
│   │   └── out/
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
│   │   ├── CreateOrderService.java               ✅ CreateOrderUseCase 구현
│   │   ├── HandlePaymentSuccessService.java      ✅ HandlePaymentSuccessUseCase 구현
│   │   ├── HandlePaymentFailureService.java      ✅ HandlePaymentFailureUseCase 구현
│   │   ├── GetOrderService.java                  [신규] GetOrderUseCase 구현
│   │   ├── GetOrderListService.java              [신규] GetOrderListUseCase 구현
│   │   ├── UpdateOrderService.java               [신규] UpdateOrderUseCase 구현
│   │   ├── DeleteOrderService.java               [신규] DeleteOrderUseCase 구현
│   │   ├── OrderService.java                     [@Deprecated - 단계적 제거]
│   │   ├── OrderModificationService.java         [@Deprecated - UpdateOrderService로 대체]
│   │   └── PaymentStatusTransitionService.java   [@Deprecated - 리팩토링 또는 제거]
│   └── dto/
│       ├── command/
│       │   ├── CreateOrderCommand.java            ✅
│       │   ├── UpdateOrderCommand.java            [신규]
│       │   └── GetOrderCommand.java               [신규, 선택]
│       └── result/
│           ├── CreateOrderResult.java             ✅
│           ├── OrderGetResult.java                [신규]
│           ├── OrderListResult.java               [신규]
│           ├── OrderUpdateResult.java             [신규]
│           └── OrderDeleteResult.java             [신규]
├── adapter/
│   ├── in/
│   │   ├── web/
│   │   │   └── OrderController.java              [이동: presentation/controller/ → adapter/in/web/]
│   │   └── kafka/
│   │       ├── PaymentKafkaConsumer.java          ✅ (kafkaOrder/payment/PaymentEventConsumer 대체)
│   │       └── InventoryKafkaConsumer.java        [신규, 필요시]
│   └── out/
│       ├── persistence/
│       │   ├── BaseJpaEntity.java                 ✅
│       │   ├── OrderJpaEntity.java                ✅
│       │   ├── OrderJpaRepository.java            ✅
│       │   ├── OrderMapper.java                   ✅
│       │   ├── OrderPersistenceAdapter.java       ✅
│       │   └── OrderQueryRepositoryImpl.java      [이동: infrastructure/repository/]
│       ├── client/
│       │   ├── BroadcastFeignAdapter.java         ✅
│       │   ├── ProductFeignAdapter.java           ✅
│       │   ├── CouponFeignAdapter.java            ✅
│       │   ├── InventoryFeignAdapter.java         [신규: InventoryPort 구현]
│       │   └── PaymentFeignAdapter.java           [신규: PaymentPort 구현]
│       └── messaging/
│           ├── KafkaOrderEventPublisher.java      ✅
│           ├── KafkaCouponEventPublisher.java     [이동: kafkaOrder/coupon/CouponUsedProducer]
│           └── KafkaInventoryEventPublisher.java  [이동: kafkaOrder/product/InventoryEventProducer]
└── presentation/
    └── controller/
        └── OrderController.java                  [@Deprecated 또는 adapter/in/web으로 통합]
```

---

## 상세 설계

### 1. 신규 UseCase 인터페이스 (domain/port/in/)

```java
// GetOrderUseCase.java
public interface GetOrderUseCase {
    /**
     * 주문 단건 조회
     * @param orderId 주문 ID
     * @param userId  요청 사용자 ID (권한 검증용)
     * @param role    사용자 권한 (ROLE_CUSTOMER이면 본인 주문만)
     */
    OrderGetResult getOrder(UUID orderId, UUID userId, String role);
}

// GetOrderListUseCase.java
public interface GetOrderListUseCase {
    /**
     * 주문 목록 조회 (페이징)
     * @param userId   요청 사용자 ID
     * @param role     사용자 권한 (ROLE_CUSTOMER이면 본인 주문만)
     * @param pageable 페이징 정보
     */
    OrderListResult getOrders(UUID userId, String role, Pageable pageable);
}

// UpdateOrderUseCase.java
public interface UpdateOrderUseCase {
    /**
     * 주문 수정 (PENDING 상태만 가능)
     * - 수량, 요청사항, 쿠폰 변경 가능
     * @param orderId 주문 ID
     * @param command 수정 내용
     * @param userId  요청 사용자 ID
     * @param role    사용자 권한
     */
    OrderUpdateResult updateOrder(UUID orderId, UpdateOrderCommand command, UUID userId, String role);
}

// DeleteOrderUseCase.java
public interface DeleteOrderUseCase {
    /**
     * 주문 소프트 삭제
     * @param orderId 주문 ID
     * @param userId  요청 사용자 ID
     * @param role    사용자 권한
     */
    OrderDeleteResult deleteOrder(UUID orderId, UUID userId, String role);
}
```

### 2. 신규 Outbound Port (domain/port/out/)

```java
// InventoryPort.java
/**
 * 재고 쓰기 작업 Port (Outbound)
 * - Kafka 이벤트 발행을 통한 재고 감소/복구
 * - ProductQueryPort는 읽기 전용 (isOrderable, findById)
 * - InventoryPort는 쓰기 전용 (decrease, rollback)
 */
public interface InventoryPort {
    /**
     * 재고 감소 이벤트 발행 (inventory-decrease Kafka topic)
     */
    void decreaseInventory(UUID productId, int quantity, UUID orderId);

    /**
     * 재고 복구 이벤트 발행 (inventory-rollback Kafka topic) — 보상 트랜잭션용
     */
    void rollbackInventory(UUID productId, int quantity, UUID orderId);
}

// PaymentPort.java
/**
 * 결제 처리 Port (Outbound)
 * - Feign 기반 동기 호출
 * - PaymentStatusTransitionService의 직접 의존 제거
 */
public interface PaymentPort {
    /**
     * 결제 준비 요청 (KakaoPay ready)
     * @return tid(트랜잭션 ID) + nextRedirectUrl
     */
    PaymentReadyResult readyPayment(UUID orderId, BigDecimal amount, String productId);

    /**
     * 결제 환불/취소 요청 (PAID → REFUNDED)
     */
    void refundPayment(UUID orderId);
}
```

**PaymentReadyResult** (adapter DTO):

```java
// application/dto/result/PaymentReadyResult.java
public record PaymentReadyResult(
    String tid,
    String nextRedirectUrl
) {}
```

### 3. 신규 Feign Adapter 구현체

```java
// adapter/out/client/InventoryFeignAdapter.java
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryFeignAdapter implements InventoryPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void decreaseInventory(UUID productId, int quantity, UUID orderId) {
        InventoryDecreaseRequestEvent event =
            new InventoryDecreaseRequestEvent(orderId, productId, quantity);
        kafkaTemplate.send("inventory-decrease", orderId.toString(), event);
        log.info("[InventoryFeignAdapter] inventory-decrease 이벤트 발행: orderId={}", orderId);
    }

    @Override
    public void rollbackInventory(UUID productId, int quantity, UUID orderId) {
        InventoryRollbackEvent event =
            new InventoryRollbackEvent(orderId, productId, quantity);
        kafkaTemplate.send("inventory-rollback", orderId.toString(), event);
        log.info("[InventoryFeignAdapter] inventory-rollback 이벤트 발행: orderId={}", orderId);
    }
}
```

> **참고**: `InventoryFeignAdapter`는 이름과 달리 Kafka 기반이다. 기존 `InventoryEventProducer.java`
> 의 로직을 Port 패턴으로 래핑한 것이며, `infrastructure/client/feign/InventoryClient.java`
> (현재 주석 처리됨)의 동기 호출은 `ProductFeignAdapter.ProductQueryPort`가 담당한다.

```java
// adapter/out/client/PaymentFeignAdapter.java
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFeignAdapter implements PaymentPort {

    private final PaymentClient paymentClient;

    @Override
    public PaymentReadyResult readyPayment(UUID orderId, BigDecimal amount, String productId) {
        ApiResponse<PaymentReadyResponseDto> response =
            paymentClient.readyPayment(new PaymentReadyRequestDto(orderId, amount, productId));
        PaymentReadyResponseDto dto = response.getData();
        if (dto == null) {
            throw new OrderDomainException("결제 준비 요청 실패");
        }
        return new PaymentReadyResult(dto.tid(), dto.nextRedirectUrl());
    }

    @Override
    public void refundPayment(UUID orderId) {
        ApiResponse<PaymentRefundResponseDto> response = paymentClient.refundPayment(orderId);
        if (response == null || response.getData() == null) {
            throw new OrderDomainException("결제 환불 요청 실패");
        }
        log.info("[PaymentFeignAdapter] 결제 환불 완료: orderId={}", orderId);
    }
}
```

### 4. 신규 Application Service 구현

#### 4-1. GetOrderService

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetOrderService implements GetOrderUseCase {

    private final OrderRepositoryPort orderRepositoryPort;

    @Override
    public OrderGetResult getOrder(UUID orderId, UUID userId, String role) {
        Order order = orderRepositoryPort.findById(orderId)
            .orElseThrow(() -> new OrderDomainException("주문을 찾을 수 없습니다."));
        validateOwnership(role, order.getUserId(), userId);
        return OrderGetResult.from(order);
    }

    private void validateOwnership(String role, UUID orderUserId, UUID currentUserId) {
        if ("ROLE_CUSTOMER".equals(role) && !orderUserId.equals(currentUserId)) {
            throw new OrderDomainException("고객은 자신의 주문만 조회할 수 있습니다.");
        }
    }
}
```

#### 4-2. GetOrderListService

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetOrderListService implements GetOrderListUseCase {

    private final OrderRepositoryPort orderRepositoryPort;

    @Override
    public OrderListResult getOrders(UUID userId, String role, Pageable pageable) {
        Page<Order> orders = "ROLE_CUSTOMER".equals(role)
            ? orderRepositoryPort.findAllByUserId(userId, pageable)
            : orderRepositoryPort.findAll(pageable);
        return OrderListResult.from(orders);
    }
}
```

#### 4-3. UpdateOrderService (OrderModificationService 리팩토링)

```java
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UpdateOrderService implements UpdateOrderUseCase {

    // Port 인터페이스만 주입 (Feign 클래스 직접 import 없음)
    private final OrderRepositoryPort orderRepositoryPort;
    private final ProductQueryPort productQueryPort;    // isOrderable, findById
    private final CouponQueryPort couponQueryPort;      // userHasCoupon, getDiscountPolicy

    @Override
    public OrderUpdateResult updateOrder(UUID orderId, UpdateOrderCommand command,
                                          UUID userId, String role) {
        Order order = orderRepositoryPort.findById(orderId)
            .orElseThrow(() -> new OrderDomainException("주문을 찾을 수 없습니다."));

        // 권한 검증
        if ("ROLE_CUSTOMER".equals(role) && !order.getUserId().equals(userId)) {
            throw new OrderDomainException("고객은 자신의 주문만 수정할 수 있습니다.");
        }

        // PENDING 상태만 수정 가능
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OrderDomainException("PENDING 상태의 주문만 수정할 수 있습니다.");
        }

        // 재고 확인
        if (!productQueryPort.isOrderable(order.getProductId(), command.productQuantity())) {
            throw new OrderDomainException("재고가 부족합니다.");
        }

        // 상품 정보 조회
        ProductInfo product = productQueryPort.findById(order.getProductId());

        // 할인 정책 조회
        DiscountPolicy discountPolicy = null;
        if (command.couponId() != null) {
            if (!couponQueryPort.userHasCoupon(userId, command.couponId())) {
                throw new OrderDomainException("보유하지 않은 쿠폰입니다.");
            }
            String couponCode = couponQueryPort.getCouponCode(userId, command.couponId());
            if (couponCode != null) {
                discountPolicy = couponQueryPort.getDiscountPolicy(couponCode);
            }
        }

        // 금액 계산 및 주문 수정 (Order 도메인 메서드 위임)
        order.applyUpdate(command.productQuantity(), product.unitPrice(),
                          command.requirement(), command.couponId(), discountPolicy);

        Order saved = orderRepositoryPort.save(order);
        return OrderUpdateResult.from(saved);
    }
}
```

#### 4-4. DeleteOrderService

```java
@Service
@RequiredArgsConstructor
@Transactional
public class DeleteOrderService implements DeleteOrderUseCase {

    private final OrderRepositoryPort orderRepositoryPort;

    @Override
    public OrderDeleteResult deleteOrder(UUID orderId, UUID userId, String role) {
        Order order = orderRepositoryPort.findById(orderId)
            .orElseThrow(() -> new OrderDomainException("주문을 찾을 수 없습니다."));

        if ("ROLE_CUSTOMER".equals(role) && !order.getUserId().equals(userId)) {
            throw new OrderDomainException("고객은 자신의 주문만 삭제할 수 있습니다.");
        }

        orderRepositoryPort.softDelete(orderId, userId.toString());
        return OrderDeleteResult.of(orderId);
    }
}
```

### 5. Controller 리팩토링 (adapter/in/web/OrderWebAdapter 또는 OrderController)

현재 `OrderController`가 `OrderService`를 직접 주입하고 있으므로, UseCase 인터페이스로 전환한다.
파일 이동은 선택 사항이나 (presentation/controller → adapter/in/web) 클래스명과 import를 수정한다.

```java
// adapter/in/web/OrderWebAdapter.java (또는 presentation/controller/OrderController.java)
@Slf4j
@RequestMapping("/api/v1/orders")
@RestController
@RequiredArgsConstructor
public class OrderWebAdapter {

    // ✅ UseCase 인터페이스만 주입 (구현체 직접 주입 없음)
    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;
    private final GetOrderListUseCase getOrderListUseCase;
    private final UpdateOrderUseCase updateOrderUseCase;
    private final DeleteOrderUseCase deleteOrderUseCase;

    @PostMapping("")
    public ResponseEntity<ApiResponse<OrderGetResult>> createOrder(
            @Valid @RequestBody OrderCreateRequest request,
            @AuthenticationPrincipal RequestUserDetails userDetails) {
        CreateOrderCommand command = CreateOrderCommand.from(request, userDetails.getUserId());
        CreateOrderResult result = createOrderUseCase.createOrder(command);
        return ResponseUtil.success(OrderGetResult.fromCreateResult(result));
    }

    @GetMapping("")
    public ResponseEntity<ApiResponse<OrderListResult>> getOrders(
            @RequestParam int page,
            @RequestParam int size,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal RequestUserDetails userDetails) {
        Pageable pageable = PageableUtil.of(page, size, sort);
        String role = userDetails.getAuthorities().iterator().next().getAuthority();
        OrderListResult result = getOrderListUseCase.getOrders(userDetails.getUserId(), role, pageable);
        return ResponseUtil.success(result);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderGetResult>> getOrder(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal RequestUserDetails userDetails) {
        String role = userDetails.getAuthorities().iterator().next().getAuthority();
        OrderGetResult result = getOrderUseCase.getOrder(orderId, userDetails.getUserId(), role);
        return ResponseUtil.success(result);
    }

    @PatchMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderUpdateResult>> updateOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderUpdateRequest request,
            @AuthenticationPrincipal RequestUserDetails userDetails) {
        UpdateOrderCommand command = UpdateOrderCommand.from(request);
        String role = userDetails.getAuthorities().iterator().next().getAuthority();
        OrderUpdateResult result = updateOrderUseCase.updateOrder(orderId, command, userDetails.getUserId(), role);
        return ResponseUtil.success(result);
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderDeleteResult>> deleteOrder(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal RequestUserDetails userDetails) {
        String role = userDetails.getAuthorities().iterator().next().getAuthority();
        OrderDeleteResult result = deleteOrderUseCase.deleteOrder(orderId, userDetails.getUserId(), role);
        return ResponseUtil.success(result);
    }
}
```

> **v2 Controller (kafkaOrder/controller/OrderControllerKafka.java) 통합 전략**:
> - `/api/v2/orders` 엔드포인트는 `/api/v1/orders`와 동일한 UseCase를 사용하도록 통합
> - 별도 컨트롤러를 유지하거나 RequestMapping을 두 버전 모두 지원하도록 변경 가능
> - `kafkaOrder/controller/OrderControllerKafka.java`는 삭제 또는 `@Deprecated` 처리

### 6. kafkaOrder/ 이전 계획

#### 6-1. kafkaOrder/product/InventoryEventProducer → adapter/out/messaging/KafkaInventoryEventPublisher

```java
// adapter/out/messaging/KafkaInventoryEventPublisher.java
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaInventoryEventPublisher implements InventoryPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void decreaseInventory(UUID productId, int quantity, UUID orderId) {
        InventoryDecreaseRequestEvent event =
            new InventoryDecreaseRequestEvent(orderId, productId, quantity);
        kafkaTemplate.send("inventory-decrease", orderId.toString(), event);
        log.info("[KafkaInventoryEventPublisher] inventory-decrease: orderId={}", orderId);
    }

    @Override
    public void rollbackInventory(UUID productId, int quantity, UUID orderId) {
        InventoryRollbackEvent event =
            new InventoryRollbackEvent(orderId, productId, quantity);
        kafkaTemplate.send("inventory-rollback", orderId.toString(), event);
        log.info("[KafkaInventoryEventPublisher] inventory-rollback: orderId={}", orderId);
    }
}
```

> **네이밍 결정**: 실제로 Kafka를 통해 이벤트를 발행하므로 `KafkaInventoryEventPublisher`로 명명.
> Port 인터페이스명 `InventoryPort`는 그대로 유지.

#### 6-2. kafkaOrder/coupon/CouponUsedProducer → adapter/out/messaging/KafkaCouponEventPublisher

```java
// domain/port/out/CouponUsedEventPort.java (신규 Port - 선택 사항)
public interface CouponUsedEventPort {
    void publishCouponUsed(UUID couponId, UUID userId, UUID orderId);
}

// adapter/out/messaging/KafkaCouponEventPublisher.java
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaCouponEventPublisher implements CouponUsedEventPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "coupon-used";

    @Override
    public void publishCouponUsed(UUID couponId, UUID userId, UUID orderId) {
        CouponUsedEvent event = new CouponUsedEvent(couponId, userId, orderId);
        kafkaTemplate.send(TOPIC, userId.toString(), event);
        log.info("[KafkaCouponEventPublisher] coupon-used 이벤트 발행: couponId={}", couponId);
    }
}
```

#### 6-3. kafkaOrder/ 서비스 삭제 전략

| 파일 | 대체 파일 | 처리 |
|------|----------|------|
| `PaymentEventConsumer.java` | `adapter/in/kafka/PaymentKafkaConsumer.java` | 삭제 |
| `OrderCreateServiceKafka.java` | `application/service/CreateOrderService.java` | 삭제 |
| `OrderServiceKafka.java` | 여러 UseCase 서비스로 분산 | 삭제 |
| `PaymentSuccessServiceKafka.java` | `HandlePaymentSuccessService.java` | 삭제 |
| `PaymentFailureServiceKafka.java` | `HandlePaymentFailureService.java` | 삭제 |
| `PaymentStatusTransitionServiceKafka.java` | `PaymentStatusTransitionService` + Port | 통합 후 삭제 |
| `OrderControllerKafka.java` | `OrderWebAdapter.java` | 삭제 |

### 7. 레거시 파일 처리

| 파일 | 처리 |
|------|------|
| `domain/repository/OrderRepository.java` | **삭제** (OrderRepositoryPort로 완전 대체됨) |
| `infrastructure/repository/OrderQueryRepository.java` | `adapter/out/persistence/OrderQueryRepositoryImpl.java`로 이동 |
| `application/service/OrderService.java` | **@Deprecated** 처리 (GetOrder/Delete 서비스로 대체 후 제거) |
| `application/service/OrderModificationService.java` | **@Deprecated** (UpdateOrderService로 대체) |
| `application/service/PaymentStatusTransitionService.java` | **@Deprecated** (Port 기반으로 리팩토링 후 제거 또는 통합) |
| `application/service/OrderCreateService.java` | **@Deprecated** (CreateOrderService.java와 혼동 주의 — 삭제) |
| `infrastructure/client/feign/InventoryClient.java` | **삭제** (이미 주석 처리됨) |

---

## 데이터 흐름 (주요 시나리오)

### 시나리오 1: 주문 생성 (hexagonal 완성 후)

```
POST /api/v1/orders
    → OrderWebAdapter.createOrder()
    → CreateOrderUseCase.createOrder(command)
        → BroadcastQueryPort.isLive()          [BroadcastFeignAdapter]
        → ProductQueryPort.findById()          [ProductFeignAdapter]
        → ProductQueryPort.isOrderable()       [ProductFeignAdapter]
        → CouponQueryPort.getDiscountPolicy()  [CouponFeignAdapter]
        → Order.create()                       [순수 도메인]
        → OrderRepositoryPort.save()           [OrderPersistenceAdapter]
    → return CreateOrderResult
```

### 시나리오 2: 결제 성공 처리 (Kafka)

```
Kafka: payment-completed
    → PaymentKafkaConsumer.listenPaymentCompleted()
    → HandlePaymentSuccessUseCase.handle(orderId, message)
        → OrderRepositoryPort.findById()       [OrderPersistenceAdapter]
        → Order.confirmPayment()               [순수 도메인 - 상태 PAID]
        → OrderRepositoryPort.save()           [OrderPersistenceAdapter]
        → InventoryPort.decreaseInventory()    [KafkaInventoryEventPublisher]
            → Kafka: inventory-decrease
        → CouponUsedEventPort.publishCouponUsed()  [KafkaCouponEventPublisher, 쿠폰 있을 경우]
            → Kafka: coupon-used
```

### 시나리오 3: 주문 상태 전환 (PENDING → READY)

```
PATCH /api/v1/orders/{orderId}/status
    → OrderWebAdapter.updateOrderStatus()
    → UpdateOrderUseCase (또는 PaymentStatusTransitionService 리팩토링)
        → OrderRepositoryPort.findById()
        → PaymentPort.readyPayment()           [PaymentFeignAdapter]
            → PaymentClient.readyPayment()     [Feign → Payment 서비스]
        → Order.changeStatus(READY)
        → OrderRepositoryPort.save()
```

---

## 구현 순서

| 순서 | 작업 | 파일 | 의존성 |
|------|------|------|--------|
| 1 | UseCase 인터페이스 4개 추가 | `domain/port/in/` | 없음 |
| 2 | InventoryPort, PaymentPort 추가 | `domain/port/out/` | 없음 |
| 3 | Result DTO 추가 | `application/dto/result/` | 없음 |
| 4 | Command DTO 추가 | `application/dto/command/` | 없음 |
| 5 | KafkaInventoryEventPublisher | `adapter/out/messaging/` | InventoryPort |
| 6 | KafkaCouponEventPublisher | `adapter/out/messaging/` | (CouponUsedEventPort) |
| 7 | PaymentFeignAdapter | `adapter/out/client/` | PaymentPort |
| 8 | GetOrderService | `application/service/` | GetOrderUseCase |
| 9 | GetOrderListService | `application/service/` | GetOrderListUseCase |
| 10 | UpdateOrderService | `application/service/` | UpdateOrderUseCase, ProductQueryPort, CouponQueryPort |
| 11 | DeleteOrderService | `application/service/` | DeleteOrderUseCase |
| 12 | OrderController/OrderWebAdapter 리팩토링 | `adapter/in/web/` 또는 `presentation/controller/` | 모든 UseCase |
| 13 | HandlePaymentSuccessService에 InventoryPort/CouponUsedEventPort 주입 | `application/service/` | InventoryPort, CouponUsedEventPort |
| 14 | kafkaOrder/ 서비스 삭제 | `kafkaOrder/service/*` | 이전 단계 완료 후 |
| 15 | kafkaOrder/payment/PaymentEventConsumer.java 삭제 | `kafkaOrder/payment/` | PaymentKafkaConsumer 검증 후 |
| 16 | kafkaOrder/ 나머지 정리 | `kafkaOrder/` 전체 | 모든 이전 완료 후 |
| 17 | domain/repository/OrderRepository.java 삭제 | `domain/repository/` | 없음 |
| 18 | OrderQueryRepository 이전 | `infrastructure/repository/` → `adapter/out/persistence/` | 없음 |
| 19 | 레거시 서비스 @Deprecated 처리 | `application/service/` 레거시 | 새 서비스 완료 후 |
| 20 | 빌드 및 테스트 | - | 모든 단계 완료 |

---

## 검증 기준 (Verification Criteria)

| 검증 항목 | 기대값 |
|----------|--------|
| `domain/port/in/` UseCase 7개 존재 | ✅ Create, HandleSuccess, HandleFailure, Get, GetList, Update, Delete |
| `domain/port/out/` Port 7개 존재 | ✅ OrderRepo, EventPublisher, Broadcast, Product, Coupon, Inventory, Payment |
| `OrderController` 또는 `OrderWebAdapter`가 UseCase 인터페이스만 주입 | Service 직접 주입 없음 |
| `application/service/*` 새 서비스가 Port 인터페이스만 주입 | Feign Client import 없음 |
| `kafkaOrder/` 폴더 삭제 | 존재하지 않음 |
| `domain/repository/OrderRepository.java` 삭제 | 존재하지 않음 |
| `adapter/out/client/InventoryFeignAdapter`, `PaymentFeignAdapter` 존재 | 존재 |
| `adapter/out/messaging/KafkaInventoryEventPublisher`, `KafkaCouponEventPublisher` 존재 | 존재 |
| 빌드 성공 (`./gradlew :order:build`) | BUILD SUCCESS |

---

## 아키텍처 원칙 (검증 체크리스트)

- [ ] `application/service/` 내 신규 서비스에 `import ...feign...` 없음
- [ ] `application/service/` 내 신규 서비스에 `import ...kafka...` 없음
- [ ] `domain/port/` 인터페이스에 Spring, JPA, Kafka, Feign import 없음
- [ ] `domain/model/Order.java`에 `import jakarta.persistence.*` 없음 (이미 달성)
- [ ] 컨트롤러(또는 웹 어댑터)가 UseCase 인터페이스만 참조

---

## 특이사항 및 결정 사항

### A. InventoryPort가 Kafka 기반인 이유

재고 쓰기 작업은 Saga 패턴으로 비동기 처리된다.
`ProductFeignAdapter`가 읽기(isOrderable, findById)를 담당하고,
`KafkaInventoryEventPublisher`가 쓰기(decrease, rollback) 이벤트를 발행한다.
Port 이름은 `InventoryPort`이나 구현체는 Kafka 기반으로 작동한다.

### B. PaymentStatusTransitionService 처리 방침

기존 `PaymentStatusTransitionService`는 PENDING→READY(결제준비) 흐름을 담당한다.
이 로직은 `UpdateOrderUseCase`에 통합하거나 별도 `InitiatePaymentUseCase`로 분리할 수 있다.
**결정**: 현재는 `@Deprecated` 처리하고 리팩토링은 다음 이터레이션에서 수행.
컨트롤러의 `updateOrderStatus()` 엔드포인트는 `PaymentStatusTransitionService`를 일시 유지.

### C. v1/v2 API 통합

`OrderController`(v1)과 `OrderControllerKafka`(v2)의 통합 방향:
- v2의 Order 생성은 이미 `CreateOrderService`(hexagonal)가 담당 → v2 컨트롤러를 v1에 통합하거나 삭제 가능
- **결정**: v2 컨트롤러 삭제, `/api/v1/orders` 단일 엔드포인트로 통합

---

## 버전 이력

| 버전 | 날짜 | 변경 내용 | 작성자 |
|------|------|---------|--------|
| 1.0 | 2026-02-15 | 최초 작성 | design-agent |
