# Design: Hexagonal Architecture + DDD Pilot (Order Service)

## 메타
- **Feature**: hexagonal-ddd-pilot
- **Phase**: Design
- **생성일**: 2026-02-13
- **참조 Plan**: `docs/01-plan/features/hexagonal-ddd-pilot.plan.md`

---

## 1. 최종 패키지 구조

```
order/src/main/java/com/live_commerce/order/
│
├── domain/                                     ← 순수 Java. 외부 의존 ZERO
│   ├── model/
│   │   ├── Order.java                          ← Aggregate Root
│   │   ├── OrderStatus.java                    ← Enum (기존 유지)
│   │   └── vo/
│   │       ├── Money.java                      ← Value Object (금액)
│   │       ├── OrderQuantity.java              ← Value Object (수량)
│   │       └── DiscountPolicy.java             ← Value Object (할인 계산 포함)
│   ├── exception/
│   │   └── OrderDomainException.java           ← Domain 전용 예외
│   └── port/
│       ├── in/                                 ← Inbound Ports (Use Case 인터페이스)
│       │   ├── CreateOrderUseCase.java
│       │   ├── HandlePaymentSuccessUseCase.java
│       │   ├── HandlePaymentFailureUseCase.java
│       │   └── QueryOrderUseCase.java
│       └── out/                                ← Outbound Ports (인프라 추상화)
│           ├── OrderRepositoryPort.java
│           ├── BroadcastQueryPort.java
│           ├── ProductQueryPort.java
│           ├── CouponQueryPort.java
│           └── OrderEventPublisher.java
│
├── application/                                ← UseCase 구현. Port만 사용
│   ├── service/
│   │   ├── CreateOrderService.java             ← CreateOrderUseCase 구현
│   │   ├── HandlePaymentSuccessService.java    ← HandlePaymentSuccessUseCase 구현
│   │   └── HandlePaymentFailureService.java
│   └── dto/
│       ├── command/
│       │   └── CreateOrderCommand.java
│       └── result/
│           ├── CreateOrderResult.java
│           └── BroadcastInfo.java              ← Port 응답용 내부 DTO
│
├── adapter/                                    ← 기존 infrastructure → adapter
│   ├── in/
│   │   ├── web/
│   │   │   └── OrderController.java            ← REST API (기존 이동)
│   │   └── kafka/
│   │       └── PaymentEventConsumer.java       ← Kafka Consumer (기존 이동)
│   └── out/
│       ├── persistence/
│       │   ├── OrderJpaEntity.java             ← @Entity (인프라 전용)
│       │   ├── BaseJpaEntity.java              ← @MappedSuperclass (기존 BaseEntity 이동)
│       │   ├── OrderJpaRepository.java         ← Spring Data JPA
│       │   ├── OrderQueryRepositoryImpl.java   ← QueryDSL (기존 이동)
│       │   └── OrderPersistenceAdapter.java    ← OrderRepositoryPort 구현
│       ├── client/
│       │   ├── feign/                          ← Feign 클라이언트 (기존 이동)
│       │   │   ├── BroadcastFeignClient.java
│       │   │   ├── ProductFeignClient.java
│       │   │   └── CouponFeignClient.java
│       │   ├── dto/                            ← Feign 전용 응답 DTO
│       │   │   └── ...
│       │   ├── BroadcastFeignAdapter.java      ← BroadcastQueryPort 구현
│       │   ├── ProductFeignAdapter.java        ← ProductQueryPort 구현
│       │   └── CouponFeignAdapter.java         ← CouponQueryPort 구현
│       └── messaging/
│           ├── KafkaOrderEventPublisher.java   ← OrderEventPublisher 구현
│           └── outbox/                         ← 기존 OutboxEventHelper (이동)
│
└── config/                                     ← Spring 설정 (기존 infrastructure/config 이동)
    ├── SecurityConfig.java
    ├── FeignConfig.java
    ├── SwaggerConfig.java
    ├── QuerydslConfig.java
    └── filter/
        ├── AuthenticationFilter.java
        └── FeignClientInterceptor.java
```

---

## 2. Domain Layer 상세 설계

### 2-1. Value Objects

#### `Money.java`
```java
package com.live_commerce.order.domain.model.vo;

import com.live_commerce.order.domain.exception.OrderDomainException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(BigDecimal amount) {

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    // compact constructor: 생성 시 유효성 검사
    public Money {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new OrderDomainException("금액은 0 이상이어야 합니다. amount=" + amount);
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(double amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public static Money of(int amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money subtract(Money other) {
        return new Money(this.amount.subtract(other.amount));
    }

    public Money multiply(int multiplier) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(multiplier)));
    }

    public boolean isGreaterThan(Money other) {
        return this.amount.compareTo(other.amount) > 0;
    }

    public double toDouble() {
        return amount.doubleValue();
    }
}
```

#### `OrderQuantity.java`
```java
package com.live_commerce.order.domain.model.vo;

import com.live_commerce.order.domain.exception.OrderDomainException;

public record OrderQuantity(int value) {

    public OrderQuantity {
        if (value < 1) {
            throw new OrderDomainException("주문 수량은 1 이상이어야 합니다. value=" + value);
        }
    }
}
```

#### `DiscountPolicy.java`
```java
package com.live_commerce.order.domain.model.vo;

import com.live_commerce.order.domain.exception.OrderDomainException;
import java.math.BigDecimal;

// 기존 DISCOUNT_TYPE enum + 할인 계산 로직을 하나의 VO로 통합
public record DiscountPolicy(Type type, BigDecimal value) {

    public enum Type { FIXED, RATE }

    public DiscountPolicy {
        if (type == null) throw new OrderDomainException("할인 타입은 null일 수 없습니다.");
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0)
            throw new OrderDomainException("할인 값은 0 이상이어야 합니다.");
        if (type == Type.RATE && value.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new OrderDomainException("할인율은 100을 초과할 수 없습니다.");
    }

    /**
     * 기존 OrderCreateService의 인라인 할인 계산 로직을 VO로 이전
     * totalPrice에 할인을 적용하여 최종 결제 금액 반환
     */
    public Money apply(Money totalPrice) {
        return switch (type) {
            case FIXED -> {
                Money discounted = totalPrice.subtract(new Money(value));
                // 최소 0원 보장
                yield discounted.isGreaterThan(Money.ZERO) ? discounted : Money.ZERO;
            }
            case RATE -> {
                BigDecimal discountAmount = totalPrice.amount()
                    .multiply(value)
                    .divide(BigDecimal.valueOf(100));
                yield totalPrice.subtract(new Money(discountAmount));
            }
        };
    }
}
```

---

### 2-2. Order Aggregate Root

```java
package com.live_commerce.order.domain.model;

import com.live_commerce.order.domain.exception.OrderDomainException;
import com.live_commerce.order.domain.model.vo.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Order Aggregate Root
 * - @Entity, @Table, Spring 어노테이션 완전 제거
 * - 도메인 로직만 포함
 * - 생성은 정적 팩토리 메서드 사용
 */
public class Order {

    private UUID id;
    private UUID userId;
    private UUID productId;
    private UUID broadcastId;
    private UUID couponId;               // nullable
    private OrderQuantity quantity;
    private Money totalPrice;            // 할인 전 금액
    private Money finalPrice;            // 최종 결제 금액
    private String requirement;
    private OrderStatus status;
    private LocalDateTime createdAt;

    // JPA용 기본 생성자 (adapter 레이어의 Mapper가 사용)
    protected Order() {}

    // 전체 필드 생성자 (Mapper용)
    private Order(UUID id, UUID userId, UUID productId, UUID broadcastId, UUID couponId,
                  OrderQuantity quantity, Money totalPrice, Money finalPrice,
                  String requirement, OrderStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.productId = productId;
        this.broadcastId = broadcastId;
        this.couponId = couponId;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.finalPrice = finalPrice;
        this.requirement = requirement;
        this.status = status;
        this.createdAt = createdAt;
    }

    /**
     * 신규 주문 생성 팩토리 메서드
     * 기존 OrderCreateRequest.toOrder() 역할을 Aggregate가 담당
     */
    public static Order create(UUID userId, UUID productId, UUID broadcastId,
                               OrderQuantity quantity, Money unitPrice,
                               UUID couponId, DiscountPolicy discountPolicy) {
        Money totalPrice = unitPrice.multiply(quantity.value());
        Money finalPrice = (discountPolicy != null)
            ? discountPolicy.apply(totalPrice)
            : totalPrice;

        return new Order(
            null,         // ID는 영속화 시 할당
            userId, productId, broadcastId, couponId,
            quantity, totalPrice, finalPrice,
            null,         // requirement 기본값
            OrderStatus.PENDING,
            LocalDateTime.now()
        );
    }

    // --- 비즈니스 메서드 ---

    public void changeStatus(OrderStatus newStatus) {
        if (this.status == OrderStatus.CANCELLED) {
            throw new OrderDomainException("이미 취소된 주문은 상태를 변경할 수 없습니다.");
        }
        if (this.status == newStatus) {
            throw new OrderDomainException("변경하려는 상태가 현재 상태와 같습니다.");
        }
        this.status = newStatus;
    }

    public void confirmPayment() {
        changeStatus(OrderStatus.PAID);
    }

    public void failOrder() {
        changeStatus(OrderStatus.FAILED);
    }

    public void cancel() {
        if (this.status == OrderStatus.PAID || this.status == OrderStatus.PROCESSING) {
            throw new OrderDomainException("결제 완료 또는 처리 중인 주문은 취소할 수 없습니다.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    // --- Getters (Lombok 제거, 명시적 getter) ---
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getProductId() { return productId; }
    public UUID getBroadcastId() { return broadcastId; }
    public UUID getCouponId() { return couponId; }
    public OrderQuantity getQuantity() { return quantity; }
    public Money getTotalPrice() { return totalPrice; }
    public Money getFinalPrice() { return finalPrice; }
    public String getRequirement() { return requirement; }
    public OrderStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // 외부에서 ID 주입 (영속화 후 어댑터가 사용)
    public void assignId(UUID id) {
        if (this.id != null) throw new OrderDomainException("이미 ID가 할당된 주문입니다.");
        this.id = id;
    }
}
```

---

### 2-3. Outbound Ports (인터페이스만)

#### `OrderRepositoryPort.java`
```java
package com.live_commerce.order.domain.port.out;

import com.live_commerce.order.domain.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepositoryPort {
    Order save(Order order);
    Optional<Order> findById(UUID orderId);
    Page<Order> findAllByUserId(UUID userId, Pageable pageable);
}
```

#### `BroadcastQueryPort.java`
```java
package com.live_commerce.order.domain.port.out;

import java.util.UUID;

public interface BroadcastQueryPort {
    boolean isLive(UUID broadcastId);   // 방송 중인지 확인
}
```

#### `ProductQueryPort.java`
```java
package com.live_commerce.order.domain.port.out;

import com.live_commerce.order.application.dto.result.ProductInfo;
import java.util.UUID;

public interface ProductQueryPort {
    ProductInfo findById(UUID productId);
    boolean isOrderable(UUID productId, int quantity);  // 재고 확인
}
```

#### `CouponQueryPort.java`
```java
package com.live_commerce.order.domain.port.out;

import com.live_commerce.order.application.dto.result.CouponInfo;
import com.live_commerce.order.domain.model.vo.DiscountPolicy;
import java.util.UUID;

public interface CouponQueryPort {
    boolean userHasCoupon(UUID userId, UUID couponId);
    DiscountPolicy getDiscountPolicy(String couponCode);  // 도메인 VO 반환
}
```

#### `OrderEventPublisher.java`
```java
package com.live_commerce.order.domain.port.out;

import com.live_commerce.order.domain.model.Order;
import java.util.UUID;

public interface OrderEventPublisher {
    void publishInventoryDecreased(Order order);
    void publishCouponUsed(UUID couponId, UUID userId);
    void publishInventoryRollback(UUID orderId, UUID productId, int quantity);
}
```

---

### 2-4. Inbound Ports (Use Case 인터페이스)

#### `CreateOrderUseCase.java`
```java
package com.live_commerce.order.domain.port.in;

import com.live_commerce.order.application.dto.command.CreateOrderCommand;
import com.live_commerce.order.application.dto.result.CreateOrderResult;

public interface CreateOrderUseCase {
    CreateOrderResult createOrder(CreateOrderCommand command);
}
```

#### `HandlePaymentSuccessUseCase.java`
```java
package com.live_commerce.order.domain.port.in;

import java.util.UUID;

public interface HandlePaymentSuccessUseCase {
    void handle(UUID orderId, String message);
}
```

---

## 3. Application Layer 상세 설계

### 3-1. Command / Result DTO

#### `CreateOrderCommand.java`
```java
package com.live_commerce.order.application.dto.command;

import java.util.UUID;

// 외부(Controller)에서 Application으로 전달되는 Command
// @Valid 어노테이션 제거 (Controller 레이어에서 검증)
public record CreateOrderCommand(
    UUID userId,
    UUID productId,
    int orderQuantity,
    String requirement,
    UUID broadcastId,
    UUID couponId    // nullable
) {}
```

#### `CreateOrderResult.java`
```java
package com.live_commerce.order.application.dto.result;

import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.model.OrderStatus;
import java.util.UUID;

public record CreateOrderResult(
    UUID orderId,
    OrderStatus status,
    double totalPrice,
    double finalPrice
) {
    public static CreateOrderResult from(Order order) {
        return new CreateOrderResult(
            order.getId(),
            order.getStatus(),
            order.getTotalPrice().toDouble(),
            order.getFinalPrice().toDouble()
        );
    }
}
```

#### `ProductInfo.java` (Port 응답용 내부 DTO)
```java
package com.live_commerce.order.application.dto.result;

import com.live_commerce.order.domain.model.vo.Money;
import java.util.UUID;

public record ProductInfo(UUID productId, String name, Money unitPrice) {}
```

---

### 3-2. CreateOrderService

```java
package com.live_commerce.order.application.service;

import com.live_commerce.order.application.dto.command.CreateOrderCommand;
import com.live_commerce.order.application.dto.result.CreateOrderResult;
import com.live_commerce.order.application.dto.result.ProductInfo;
import com.live_commerce.order.domain.exception.OrderDomainException;
import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.model.vo.*;
import com.live_commerce.order.domain.port.in.CreateOrderUseCase;
import com.live_commerce.order.domain.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateOrderService implements CreateOrderUseCase {

    // ✅ Port(인터페이스)만 주입 - Feign 클래스 import 없음
    private final BroadcastQueryPort broadcastQueryPort;
    private final ProductQueryPort productQueryPort;
    private final CouponQueryPort couponQueryPort;
    private final OrderRepositoryPort orderRepositoryPort;

    @Override
    @Transactional
    public CreateOrderResult createOrder(CreateOrderCommand command) {

        // 1. 방송 중인지 확인
        if (!broadcastQueryPort.isLive(command.broadcastId())) {
            throw new OrderDomainException("방송 중일 때만 주문이 가능합니다.");
        }

        // 2. 상품 정보 조회 + 재고 확인
        ProductInfo product = productQueryPort.findById(command.productId());
        if (!productQueryPort.isOrderable(command.productId(), command.orderQuantity())) {
            throw new OrderDomainException("재고가 없는 상태입니다.");
        }

        // 3. 할인 정책 조회 (쿠폰이 있는 경우)
        DiscountPolicy discountPolicy = null;
        if (command.couponId() != null) {
            if (!couponQueryPort.userHasCoupon(command.userId(), command.couponId())) {
                throw new OrderDomainException("보유하지 않은 쿠폰입니다.");
            }
            // couponCode 조회 후 정책 가져오기
            // (실제로는 CouponQueryPort에 userId+couponId로 policy 반환하도록 조정 가능)
        }

        // 4. Order Aggregate 생성 - 할인 계산은 DiscountPolicy.apply()가 담당
        Order order = Order.create(
            command.userId(),
            command.productId(),
            command.broadcastId(),
            new OrderQuantity(command.orderQuantity()),
            product.unitPrice(),
            command.couponId(),
            discountPolicy
        );

        // 5. 저장
        Order savedOrder = orderRepositoryPort.save(order);
        return CreateOrderResult.from(savedOrder);
    }
}
```

---

## 4. Adapter Layer 상세 설계

### 4-1. Persistence Adapter

#### `BaseJpaEntity.java` (기존 presentation.common.BaseEntity 이전)
```java
package com.live_commerce.order.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.data.annotation.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;

@Getter
@EntityListeners(AuditingEntityListener.class)
@MappedSuperclass
public abstract class BaseJpaEntity {
    @CreatedDate @Column(updatable = false)
    private LocalDateTime createdAt;
    @CreatedBy @Column(updatable = false)
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
    private String deletedBy;
    protected Boolean deletedStatus;

    public void delete(String deletedBy) {
        this.deletedStatus = Boolean.TRUE;
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
    }
}
```

#### `OrderJpaEntity.java` (JPA 전용 엔티티)
```java
package com.live_commerce.order.adapter.out.persistence;

import com.live_commerce.order.domain.model.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity @Table(name = "p_order", schema = "orders")
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class OrderJpaEntity extends BaseJpaEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID productId;
    private UUID userId;
    @Column(name = "product_quantity")
    private Integer productQuantity;
    @Column(name = "product_total_price")
    private Double productTotalPrice;
    private String requirement;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    private UUID broadcastId;
    private UUID couponId;
    @Column(name = "final_paid_price")
    private Double finalPaidPrice;
}
```

#### `OrderPersistenceAdapter.java` (OrderRepositoryPort 구현)
```java
package com.live_commerce.order.adapter.out.persistence;

import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.port.out.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
@RequiredArgsConstructor
public class OrderPersistenceAdapter implements OrderRepositoryPort {

    private final OrderJpaRepository jpaRepository;
    private final OrderMapper mapper;   // domain ↔ JPA entity 변환

    @Override
    public Order save(Order order) {
        OrderJpaEntity entity = mapper.toJpaEntity(order);
        OrderJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Order> findById(UUID orderId) {
        return jpaRepository.findById(orderId).map(mapper::toDomain);
    }

    @Override
    public Page<Order> findAllByUserId(UUID userId, Pageable pageable) {
        return jpaRepository.findAllByUserId(userId, pageable).map(mapper::toDomain);
    }
}
```

#### `OrderMapper.java` (Domain ↔ JPA Entity 변환)
```java
package com.live_commerce.order.adapter.out.persistence;

import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.model.vo.Money;
import com.live_commerce.order.domain.model.vo.OrderQuantity;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public OrderJpaEntity toJpaEntity(Order order) {
        return OrderJpaEntity.builder()
            .id(order.getId())
            .userId(order.getUserId())
            .productId(order.getProductId())
            .productQuantity(order.getQuantity().value())
            .productTotalPrice(order.getTotalPrice().toDouble())
            .finalPaidPrice(order.getFinalPrice().toDouble())
            .requirement(order.getRequirement())
            .status(order.getStatus())
            .broadcastId(order.getBroadcastId())
            .couponId(order.getCouponId())
            .build();
    }

    public Order toDomain(OrderJpaEntity entity) {
        // Order 내부 생성자 호출을 위해 리플렉션 또는 패키지 접근자 사용
        // 실용적 접근: Order에 package-private 정적 팩토리 추가
        return Order.reconstitute(
            entity.getId(),
            entity.getUserId(),
            entity.getProductId(),
            entity.getBroadcastId(),
            entity.getCouponId(),
            new OrderQuantity(entity.getProductQuantity()),
            Money.of(entity.getProductTotalPrice()),
            Money.of(entity.getFinalPaidPrice()),
            entity.getRequirement(),
            entity.getStatus(),
            entity.getCreatedAt()
        );
    }
}
```

> **참고:** `Order.reconstitute()` - DB에서 재구성할 때 사용하는 정적 팩토리 메서드를 `Order`에 추가 (유효성 검사 skip용)

---

### 4-2. Feign Adapters

#### `BroadcastFeignAdapter.java`
```java
package com.live_commerce.order.adapter.out.client;

import com.live_commerce.order.domain.port.out.BroadcastQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BroadcastFeignAdapter implements BroadcastQueryPort {

    private final BroadcastFeignClient broadcastFeignClient; // 기존 Feign 인터페이스

    @Override
    public boolean isLive(UUID broadcastId) {
        var response = broadcastFeignClient.getBroadcast(broadcastId);
        var statusResponse = response.getData();
        return statusResponse != null
            && BroadcastStatus.LIVE == statusResponse.getBroadcastStatus();
    }
}
```

#### `CouponFeignAdapter.java` (핵심 - 할인 정책을 VO로 변환)
```java
package com.live_commerce.order.adapter.out.client;

import com.live_commerce.order.domain.model.vo.DiscountPolicy;
import com.live_commerce.order.domain.port.out.CouponQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CouponFeignAdapter implements CouponQueryPort {

    private final CouponFeignClient couponFeignClient;

    @Override
    public boolean userHasCoupon(UUID userId, UUID couponId) {
        var couponList = couponFeignClient.getIssuedCoupons().getData();
        if (couponList == null || couponList.coupons() == null) return false;
        return couponList.coupons().stream()
            .anyMatch(c -> c.id().equals(couponId));
    }

    @Override
    public DiscountPolicy getDiscountPolicy(String couponCode) {
        var policy = couponFeignClient.getCouponPolicy(couponCode).getData();
        // Feign DTO → Domain VO 변환 (어댑터의 핵심 역할)
        DiscountPolicy.Type type = policy.discountType() == FIXED
            ? DiscountPolicy.Type.FIXED
            : DiscountPolicy.Type.RATE;
        return new DiscountPolicy(type, BigDecimal.valueOf(policy.discountValue()));
    }
}
```

---

### 4-3. Inbound Kafka Adapter

```java
package com.live_commerce.order.adapter.in.kafka;

// 기존 PaymentEventConsumer - 위치만 이동 (adapter/in/kafka/)
// 변경사항: KafkaService 대신 UseCase 인터페이스 주입

@Component @RequiredArgsConstructor @Slf4j
public class PaymentEventConsumer {

    // ✅ 기존: PaymentSuccessServiceKafka (구현체 직접 주입)
    // ✅ 변경: HandlePaymentSuccessUseCase (인터페이스 주입)
    private final HandlePaymentSuccessUseCase handlePaymentSuccessUseCase;
    private final HandlePaymentFailureUseCase handlePaymentFailureUseCase;

    @KafkaListener(topics = "payment-completed", groupId = "order")
    public void listenPaymentCompleted(PaymentCompletedEvent msg) {
        handlePaymentSuccessUseCase.handle(msg.orderId(), msg.message());
    }

    @KafkaListener(topics = "payment-failed", groupId = "order")
    public void listenPaymentFailed(PaymentFailedEvent event) {
        handlePaymentFailureUseCase.handle(event.orderId(), event.message());
    }
}
```

---

## 5. 의존성 방향 검증

```
[변경 전] 잘못된 의존성:
  Order → BaseEntity(presentation) ← 역방향!
  Order → @Entity(JPA)             ← 도메인이 인프라에 의존
  OrderCreateService → BroadcastClient(Feign) ← 앱이 인프라에 의존

[변경 후] 올바른 의존성:
  presentation → Order
  Order ← (아무것도 없음, 순수 Java)
  OrderCreateService → BroadcastQueryPort(인터페이스) ← 추상화에 의존
  BroadcastFeignAdapter → BroadcastQueryPort 구현 ← 인프라가 포트를 구현
```

```
의존성 방향 (화살표 = "의존"):

  adapter/in/web  →  application/service
  adapter/in/kafka →  domain/port/in (UseCase)
  application/service →  domain/port/out (Port)
  adapter/out/* →  domain/port/out (implements)
  domain ← (아무것도 없음)
```

---

## 6. 마이그레이션 전략 (단계별)

> 기존 기능을 유지하면서 점진적으로 전환합니다.

### Step 1: Domain Layer 순수화 (2-3시간)

**목표:** Order 엔티티에서 JPA/Spring 의존성 제거

1. `domain/exception/OrderDomainException.java` 생성
2. `domain/model/vo/Money.java`, `OrderQuantity.java`, `DiscountPolicy.java` 생성
3. `Order.java` 순수화:
   - `@Entity`, `@Table`, `@Column` 제거
   - `BaseEntity` 상속 제거
   - `application.exception` import 제거 → `domain.exception` 사용
   - `DISCOUNT_TYPE` → `DiscountPolicy.Type` 으로 교체
4. `Order.reconstitute()` 정적 팩토리 추가
5. 빌드 오류 해결

### Step 2: Port 인터페이스 정의 (1시간)

1. `domain/port/in/` 하위 UseCase 인터페이스 4개 생성
2. `domain/port/out/` 하위 Port 인터페이스 5개 생성
3. 기존 `OrderRepository`는 임시 유지 (Step 4에서 교체)

### Step 3: Adapter 레이어 구성 (3-4시간)

1. `adapter/out/persistence/` 생성:
   - `BaseJpaEntity.java` (기존 BaseEntity 복사 후 이전)
   - `OrderJpaEntity.java` (@Entity 로직 이전)
   - `OrderMapper.java` 생성
   - `OrderPersistenceAdapter.java` (OrderRepositoryPort 구현)
2. `adapter/out/client/` 생성:
   - `BroadcastFeignAdapter.java`
   - `ProductFeignAdapter.java`
   - `CouponFeignAdapter.java`
3. `adapter/out/messaging/` 생성:
   - `KafkaOrderEventPublisher.java`

### Step 4: Application Service 교체 (2시간)

1. `CreateOrderService` → Port 주입으로 교체
2. `HandlePaymentSuccessService` 생성 (기존 `PaymentSuccessServiceKafka` 로직 이전)
3. `HandlePaymentFailureService` 생성

### Step 5: Inbound Adapter & Config 이전 (1시간)

1. `OrderController` → `adapter/in/web/` 이전
2. `PaymentEventConsumer` → `adapter/in/kafka/` 이전
3. `config/` 패키지로 설정 클래스 이전
4. 기존 `infrastructure/`, `kafkaOrder/` 패키지 제거

### Step 6: 테스트 & 검증 (2시간)

1. Domain 단위 테스트 (Mock 없이 순수 Java 테스트)
2. Application 레이어 테스트 (Port Mock 주입)
3. 통합 테스트 (전체 API 호출)
4. 빌드 확인: `./gradlew :order:build`

---

## 7. 핵심 학습 포인트 (이 설계의 핵심)

| 개념 | Before | After |
|------|--------|-------|
| Domain 순수성 | `@Entity`, Spring import 있음 | 순수 Java record/class |
| 의존성 방향 | App → Infra (직접) | App → Port ← Adapter (역전) |
| 도메인 로직 위치 | Service에 인라인 | VO(DiscountPolicy)에 캡슐화 |
| 테스트 용이성 | Feign Mock 필수 | Port Mock으로 간단 테스트 |
| 패키지 응집도 | `kafkaOrder/` 분산 | `adapter/in/kafka/` 응집 |

---

## 8. 제외 범위 (이번 파일럿 범위 아님)

- Payment 서비스 동일 패턴 적용 (검증 후 다음 단계)
- Event Sourcing 패턴 (향후 확장)
- CQRS 분리 (조회 모델 별도화)
- 다른 마이크로서비스 전파
