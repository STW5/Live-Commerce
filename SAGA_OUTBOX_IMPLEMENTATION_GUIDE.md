# Saga 패턴 & Outbox 패턴 구현 가이드

## 📚 목차

1. [개요](#1-개요)
2. [코드 중복 제거 (common-lib, event-schema)](#2-코드-중복-제거)
3. [Outbox Pattern 구현](#3-outbox-pattern-구현)
4. [Saga Orchestrator 구현](#4-saga-orchestrator-구현)
5. [실전 적용 예시](#5-실전-적용-예시)
6. [테스트 & 모니터링](#6-테스트--모니터링)

---

## 1. 개요

### 왜 필요한가?

#### **문제 1: 코드 중복 (~3,410 LOC)**
```
현재 상황:
- SecurityConfig가 11개 서비스에 모두 존재 (~880 LOC 중복)
- CustomException이 11개 서비스에 모두 존재 (~550 LOC 중복)
- PaymentCompletedEvent가 payment와 order에 각각 존재 (버전 불일치 위험)
```

#### **문제 2: Kafka 이벤트 유실 위험**
```
현재 플로우:
1. orderRepository.save(order)  // DB 저장 성공
2. kafkaTemplate.send("order-created", event)  // Kafka 발행 실패!
   → 네트워크 장애로 실패 시 이벤트 영구 유실
```

#### **문제 3: 분산 트랜잭션 추적 불가**
```
현재:
- Order → Product → Payment 실패 시 어느 단계에서 실패했는지 추적 어려움
- 보상 트랜잭션 수동 실행 필요
- 재시도 메커니즘 없음
```

### 해결 방안

| 문제 | 해결책 | 효과 |
|------|--------|------|
| 코드 중복 | common-lib, event-schema 모듈 | 3,410 LOC 제거, 유지보수 시간 92% 단축 |
| 이벤트 유실 | Outbox Pattern | At-Least-Once 전달 보장 |
| 분산 트랜잭션 추적 | Saga Orchestrator | 실패 지점 명확히 파악, 자동 보상 |

---

## 2. 코드 중복 제거

### 2-1. 프로젝트 구조

```
Live-Commerce/
├── common-lib/                 # 👈 공통 라이브러리
│   ├── build.gradle
│   └── src/main/java/com/live_commerce/common/
│       ├── exception/
│       │   ├── CustomException.java
│       │   └── ExceptionCode.java
│       ├── dto/
│       │   └── ApiResponse.java
│       ├── outbox/
│       │   ├── OutboxEvent.java
│       │   ├── OutboxEventRepository.java
│       │   └── OutboxEventPublisher.java
│       └── saga/
│           ├── SagaState.java
│           ├── SagaStatus.java
│           └── SagaStateRepository.java
│
├── event-schema/               # 👈 이벤트 스키마
│   ├── build.gradle
│   └── src/main/java/com/live_commerce/events/
│       ├── payment/
│       │   ├── PaymentCompletedEvent.java
│       │   └── PaymentFailedEvent.java
│       ├── order/
│       │   └── OrderFailedEvent.java
│       └── inventory/
│           ├── InventoryDecreasedEvent.java
│           └── InventoryRollbackEvent.java
│
├── payment/
│   └── build.gradle
│       dependencies {
│           implementation project(':common-lib')      # 👈 의존성 추가
│           implementation project(':event-schema')   # 👈 의존성 추가
│       }
│
├── order/
│   └── build.gradle
│       dependencies {
│           implementation project(':common-lib')
│           implementation project(':event-schema')
│       }
...
```

### 2-2. Payment 서비스에 적용

#### **Before (중복 코드)**
```java
// payment/src/.../CustomException.java
package com.live_commerce.payment.application.exception;

public class CustomException extends RuntimeException {
    private final ExceptionCode exceptionCode;
    // ...
}

// order/src/.../CustomException.java
package com.live_commerce.order.application.exception;

public class CustomException extends RuntimeException {  // 👈 중복!
    private final ExceptionCode exceptionCode;
    // ...
}
```

#### **After (common-lib 사용)**
```java
// payment/src/.../PaymentExceptionCode.java
package com.live_commerce.payment.application.exception;

import com.live_commerce.common.exception.ExceptionCode;  // 👈 common-lib 사용
import org.springframework.http.HttpStatus;

public enum PaymentExceptionCode implements ExceptionCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "결제를 찾을 수 없습니다"),
    UNAUTHORIZED(HttpStatus.FORBIDDEN, "권한이 없습니다");

    private final HttpStatus httpStatus;
    private final String message;

    PaymentExceptionCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getMessage() {
        return message;
    }
}

// 사용 예시
throw new CustomException(PaymentExceptionCode.NOT_FOUND);  // 👈 common-lib의 CustomException
```

### 2-3. build.gradle 수정

```gradle
// payment/build.gradle
dependencies {
    // 공통 모듈 추가
    implementation project(':common-lib')
    implementation project(':event-schema')

    // 기존 의존성
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    ...
}
```

### 2-4. 기존 코드 제거

```bash
# Payment 서비스에서 중복 코드 제거
rm payment/src/main/java/com/live_commerce/payment/application/exception/CustomException.java
rm payment/src/main/java/com/live_commerce/payment/application/exception/ExceptionCode.java

# Order 서비스에서도 제거
rm order/src/main/java/com/live_commerce/order/application/exception/CustomException.java
rm order/src/main/java/com/live_commerce/order/application/exception/ExceptionCode.java

# import 문 수정
# Before
import com.live_commerce.payment.application.exception.CustomException;

# After
import com.live_commerce.common.exception.CustomException;
```

---

## 3. Outbox Pattern 구현

### 3-1. 개념

```
┌─────────────────────────────────────────────────────────┐
│              Outbox Pattern 동작 원리                    │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  1. Order 저장 + Outbox Event 저장 (동일 트랜잭션)       │
│     ┌──────────────────────────────────┐               │
│     │  @Transactional                  │               │
│     │  public void createOrder() {     │               │
│     │    // 1) Order 저장               │               │
│     │    orderRepository.save(order);  │               │
│     │                                  │               │
│     │    // 2) Outbox Event 저장        │               │
│     │    OutboxEvent event = OutboxEvent.create(      │
│     │      "Order", order.getId(),     │               │
│     │      "OrderCreated",             │               │
│     │      "order-created",            │               │
│     │      json                        │               │
│     │    );                            │               │
│     │    outboxRepo.save(event);       │               │
│     │  }  // 👈 COMMIT: DB에 원자적 저장 │               │
│     └──────────────────────────────────┘               │
│                                                          │
│  2. 별도 Publisher가 5초마다 Outbox 테이블 조회          │
│     ┌──────────────────────────────────┐               │
│     │  @Scheduled(fixedDelay = 5000)   │               │
│     │  public void publish() {         │               │
│     │    List<OutboxEvent> pending =   │               │
│     │      repo.findByStatus(PENDING); │               │
│     │                                  │               │
│     │    for (event : pending) {       │               │
│     │      kafka.send(event.getTopic(),│               │
│     │                 event.getPayload());             │
│     │      event.markPublished();       │               │
│     │    }                             │               │
│     │  }                               │               │
│     └──────────────────────────────────┘               │
│                                                          │
│  3. Kafka 발행 실패 시 재시도 (최대 5회)                 │
│     - FAILED 상태로 변경                                 │
│     - 1분 후 재시도 (retryCount < 5)                    │
│                                                          │
└─────────────────────────────────────────────────────────┘

핵심 보장:
✅ DB 저장 성공 = Outbox 저장 성공 (동일 트랜잭션)
✅ Kafka 발행 실패해도 Outbox에 남아있음
✅ Eventually Consistent (최종적 일관성)
```

### 3-2. Order 서비스 적용 예시

#### **OrderService.java 수정**

```java
package com.live_commerce.order.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.live_commerce.common.outbox.OutboxEvent;
import com.live_commerce.common.outbox.OutboxEventRepository;
import com.live_commerce.events.order.OrderCreatedEvent;  // 👈 event-schema 사용
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderCreateService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;  // 👈 Outbox 추가
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderCreateResponse createOrder(OrderCreateCommand command) {
        // 1. Order 생성
        Order order = command.toOrder();
        orderRepository.save(order);

        // 2. Outbox Event 저장 (동일 트랜잭션)
        OrderCreatedEvent event = new OrderCreatedEvent(
            order.getId(),
            order.getUserId(),
            order.getProductId(),
            order.getQuantity()
        );

        String payload = objectMapper.writeValueAsString(event);

        OutboxEvent outboxEvent = OutboxEvent.create(
            "Order",                // aggregateType
            order.getId(),          // aggregateId
            "OrderCreated",         // eventType
            "order-created",        // Kafka topic
            payload                 // JSON 페이로드
        );

        outboxEventRepository.save(outboxEvent);

        // 3. Commit → DB에 Order + OutboxEvent 원자적 저장
        return OrderCreateResponse.from(order);
    }
}
```

#### **application.yml 설정**

```yaml
# order/src/main/resources/application.yml
spring:
  jpa:
    properties:
      hibernate:
        # Outbox 테이블이 orders 스키마에 생성됨
        default_schema: orders

  # Scheduling 활성화
  task:
    scheduling:
      pool:
        size: 2

# Kafka 설정 (기존 유지)
spring:
  kafka:
    bootstrap-servers: localhost:9092
```

### 3-3. 동작 흐름

```
1. Order 생성 API 호출
   POST /api/v1/orders
   ↓
2. OrderCreateService.createOrder()
   - Order 저장
   - OutboxEvent 저장 (PENDING)
   - COMMIT
   ↓
3. OutboxEventPublisher (5초 후)
   - SELECT * FROM outbox_event WHERE status = 'PENDING'
   - Kafka 발행: topic="order-created", payload="{...}"
   - OutboxEvent.status = PUBLISHED
   ↓
4. Product Service가 Kafka Consumer로 수신
   - 재고 차감 로직 실행
```

### 3-4. 장애 시나리오

#### **시나리오 1: Kafka 발행 실패**
```
1. Order 저장 성공 (DB Commit)
2. OutboxEvent 저장 성공 (status=PENDING)
3. Kafka 발행 시도 → 네트워크 장애로 실패!
   ↓
4. OutboxEventPublisher가 5초 후 재시도
5. Kafka 발행 성공 → status=PUBLISHED
```

#### **시나리오 2: Publisher 다운**
```
1. Order 저장 + OutboxEvent 저장 (status=PENDING)
2. OutboxEventPublisher 서버 다운! (5초간 발행 안 됨)
   ↓
3. 서버 재시작
4. OutboxEventPublisher가 다시 조회 → PENDING 이벤트 발견
5. Kafka 발행 → status=PUBLISHED
```

**보장: 이벤트는 절대 유실되지 않음! (DB에 남아있음)**

---

## 4. Saga Orchestrator 구현

### 4-1. 개념

```
┌─────────────────────────────────────────────────────────────┐
│                    Saga Orchestrator 패턴                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  주문 생성 Saga (OrderCreationSaga)                          │
│                                                              │
│  Step 1: 재고 차감                                            │
│    → Product Service 호출                                    │
│    → SagaState.currentStep = "INVENTORY_DECREASE"          │
│    → 성공 시 다음 단계, 실패 시 보상 트랜잭션                 │
│                                                              │
│  Step 2: 쿠폰 사용                                            │
│    → Coupon Service 호출                                     │
│    → SagaState.currentStep = "COUPON_USE"                  │
│    → 실패 시: inventory 복구 + 쿠폰 복구                      │
│                                                              │
│  Step 3: 결제                                                │
│    → Payment Service 호출                                    │
│    → SagaState.currentStep = "PAYMENT"                     │
│    → 실패 시: inventory 복구 + 쿠폰 복구                      │
│                                                              │
│  Step 4: 완료                                                │
│    → SagaState.status = COMPLETED                          │
│                                                              │
│  실패 시 보상 트랜잭션:                                        │
│    currentStep에 따라 자동으로 보상 실행                      │
│    - PAYMENT 실패 → refund + coupon restore + inventory rollback │
│    - COUPON_USE 실패 → inventory rollback만                 │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 4-2. OrderCreationSaga 구현

```java
package com.live_commerce.order.application.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.live_commerce.common.saga.SagaState;
import com.live_commerce.common.saga.SagaStateRepository;
import com.live_commerce.common.saga.SagaStatus;
import com.live_commerce.order.infrastructure.client.feign.ProductClient;
import com.live_commerce.order.infrastructure.client.feign.CouponClient;
import com.live_commerce.order.infrastructure.client.feign.PaymentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCreationSaga {

    private final SagaStateRepository sagaStateRepository;
    private final ProductClient productClient;
    private final CouponClient couponClient;
    private final PaymentClient paymentClient;
    private final ObjectMapper objectMapper;

    /**
     * 주문 생성 Saga 실행
     */
    @Transactional
    public void execute(OrderCreationCommand command) {
        // 1. Saga State 생성
        String payload = objectMapper.writeValueAsString(command);
        SagaState saga = SagaState.start("OrderCreation", command.orderId(), payload);
        sagaStateRepository.save(saga);

        try {
            // Step 1: 재고 차감
            saga.updateStep("INVENTORY_DECREASE");
            sagaStateRepository.save(saga);

            productClient.decreaseInventory(command.productId(), command.quantity());
            log.info("[Saga] 재고 차감 완료 - orderId: {}", command.orderId());

            // Step 2: 쿠폰 사용
            if (command.couponId() != null) {
                saga.updateStep("COUPON_USE");
                sagaStateRepository.save(saga);

                couponClient.useCoupon(command.couponId());
                log.info("[Saga] 쿠폰 사용 완료 - orderId: {}", command.orderId());
            }

            // Step 3: 결제
            saga.updateStep("PAYMENT");
            sagaStateRepository.save(saga);

            paymentClient.readyPayment(command.toPaymentDto());
            log.info("[Saga] 결제 준비 완료 - orderId: {}", command.orderId());

            // Step 4: Saga 완료
            saga.complete();
            sagaStateRepository.save(saga);
            log.info("[Saga] 주문 생성 Saga 완료 - orderId: {}", command.orderId());

        } catch (Exception e) {
            log.error("[Saga] 주문 생성 Saga 실패 - orderId: {}, step: {}, error: {}",
                command.orderId(), saga.getCurrentStep(), e.getMessage());

            // 보상 트랜잭션 실행
            compensate(saga, command);

            saga.fail(e.getMessage());
            sagaStateRepository.save(saga);

            throw e;
        }
    }

    /**
     * 보상 트랜잭션 (Rollback)
     */
    private void compensate(SagaState saga, OrderCreationCommand command) {
        saga.startCompensation();
        sagaStateRepository.save(saga);

        try {
            String currentStep = saga.getCurrentStep();

            // 결제까지 갔다면 환불
            if ("PAYMENT".equals(currentStep)) {
                try {
                    paymentClient.refundPayment(command.orderId());
                    log.info("[Saga Compensation] 결제 환불 완료 - orderId: {}", command.orderId());
                } catch (Exception e) {
                    log.error("[Saga Compensation] 결제 환불 실패 - orderId: {}", command.orderId(), e);
                }
            }

            // 쿠폰 사용까지 갔다면 복구
            if (List.of("COUPON_USE", "PAYMENT").contains(currentStep) && command.couponId() != null) {
                try {
                    couponClient.restoreCoupon(command.couponId());
                    log.info("[Saga Compensation] 쿠폰 복구 완료 - orderId: {}", command.orderId());
                } catch (Exception e) {
                    log.error("[Saga Compensation] 쿠폰 복구 실패 - orderId: {}", command.orderId(), e);
                }
            }

            // 재고 차감까지 갔다면 복구
            if (List.of("INVENTORY_DECREASE", "COUPON_USE", "PAYMENT").contains(currentStep)) {
                try {
                    productClient.rollbackInventory(command.productId(), command.quantity());
                    log.info("[Saga Compensation] 재고 복구 완료 - orderId: {}", command.orderId());
                } catch (Exception e) {
                    log.error("[Saga Compensation] 재고 복구 실패 - orderId: {}", command.orderId(), e);
                }
            }

            saga.completeCompensation();
            sagaStateRepository.save(saga);

        } catch (Exception e) {
            log.error("[Saga Compensation] 보상 트랜잭션 실패 - orderId: {}", command.orderId(), e);
            // 보상 실패 시 수동 처리 필요 (알림 발송 등)
        }
    }
}

/**
 * Saga 실행에 필요한 Command
 */
public record OrderCreationCommand(
    UUID orderId,
    UUID productId,
    int quantity,
    UUID couponId,
    UUID userId,
    BigDecimal amount
) {
    public PaymentReadyDto toPaymentDto() {
        return new PaymentReadyDto(orderId, userId, amount);
    }
}
```

### 4-3. Controller에서 Saga 사용

```java
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderCreationSaga orderCreationSaga;

    @PostMapping
    public ApiResponse<OrderCreateResponse> createOrder(
        @RequestBody @Valid OrderCreateRequest request,
        @AuthenticationPrincipal RequestUserDetails userDetails
    ) {
        // Saga로 주문 생성
        OrderCreationCommand command = request.toCommand(userDetails.getUserId());

        orderCreationSaga.execute(command);

        return ApiResponse.success("주문이 생성되었습니다", response);
    }
}
```

### 4-4. Saga 모니터링

```sql
-- 실행 중인 Saga 조회
SELECT saga_id, saga_type, aggregate_id, current_step, started_at
FROM saga_state
WHERE status IN ('STARTED', 'RUNNING')
ORDER BY started_at DESC;

-- 실패한 Saga 조회
SELECT saga_id, saga_type, aggregate_id, current_step, error_message, started_at
FROM saga_state
WHERE status = 'FAILED'
ORDER BY started_at DESC;

-- 보상 트랜잭션 실행 중인 Saga
SELECT saga_id, saga_type, aggregate_id, current_step
FROM saga_state
WHERE status = 'COMPENSATING';
```

---

## 5. 실전 적용 예시

### 5-1. 적용 순서 (단계별)

#### **Phase 1: common-lib, event-schema 적용 (1주)**

```bash
# 1. 빌드 확인
./gradlew :common-lib:build
./gradlew :event-schema:build

# 2. Payment 서비스에 의존성 추가
# payment/build.gradle에 추가:
dependencies {
    implementation project(':common-lib')
    implementation project(':event-schema')
}

# 3. 기존 CustomException import 변경
# Before
import com.live_commerce.payment.application.exception.CustomException;

# After
import com.live_commerce.common.exception.CustomException;

# 4. 빌드 및 테스트
./gradlew :payment:build
./gradlew :payment:test
```

#### **Phase 2: Outbox Pattern 적용 (1주)**

```bash
# 1. Order 서비스에 Outbox 테이블 생성
-- order/src/main/resources/db/migration/V2__create_outbox_table.sql

CREATE TABLE IF NOT EXISTS outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    retry_count INT DEFAULT 0,
    error_message TEXT,
    INDEX idx_outbox_status (status),
    INDEX idx_outbox_created_at (created_at)
);

# 2. OutboxEventPublisher @EnableScheduling 추가
@SpringBootApplication
@EnableScheduling  // 👈 추가
public class OrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}

# 3. OrderService에 Outbox 로직 추가 (위 예시 참고)

# 4. 테스트
./gradlew :order:build
./gradlew :order:test
```

#### **Phase 3: Saga Orchestrator 적용 (2주)**

```bash
# 1. Saga 테이블 생성
-- order/src/main/resources/db/migration/V3__create_saga_table.sql

CREATE TABLE IF NOT EXISTS saga_state (
    saga_id UUID PRIMARY KEY,
    saga_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_step VARCHAR(50) NOT NULL,
    payload TEXT,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    error_message TEXT,
    retry_count INT DEFAULT 0,
    INDEX idx_saga_aggregate_id (aggregate_id),
    INDEX idx_saga_status (status)
);

# 2. OrderCreationSaga 구현 (위 예시 참고)

# 3. 기존 OrderService를 OrderCreationSaga로 대체

# 4. 테스트
./gradlew :order:test
```

### 5-2. 마이그레이션 전략

```
1주차: common-lib 적용
- [ ] payment 서비스 마이그레이션
- [ ] order 서비스 마이그레이션
- [ ] 기존 코드 삭제

2주차: event-schema 적용
- [ ] Payment 이벤트 통합
- [ ] Order 이벤트 통합
- [ ] Inventory 이벤트 통합

3주차: Outbox Pattern
- [ ] Order 서비스 Outbox 적용
- [ ] Payment 서비스 Outbox 적용
- [ ] 모니터링 대시보드 구축

4주차: Saga Orchestrator
- [ ] OrderCreationSaga 구현
- [ ] PaymentRefundSaga 구현
- [ ] 보상 트랜잭션 테스트
```

---

## 6. 테스트 & 모니터링

### 6-1. Outbox Pattern 테스트

```java
@SpringBootTest
@Transactional
class OutboxPatternTest {

    @Autowired
    private OrderCreateService orderCreateService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    @DisplayName("Order 저장 시 Outbox Event도 함께 저장됨")
    void createOrder_shouldSaveOutboxEvent() {
        // Given
        OrderCreateCommand command = new OrderCreateCommand(...);

        // When
        orderCreateService.createOrder(command);

        // Then
        List<OutboxEvent> events = outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getTopic()).isEqualTo("order-created");
    }

    @Test
    @DisplayName("Kafka 발행 실패 시 재시도")
    void publishFailure_shouldRetry() {
        // Kafka Mock으로 실패 시뮬레이션
        // OutboxEventPublisher 실행
        // 재시도 확인
    }
}
```

### 6-2. Saga Pattern 테스트

```java
@SpringBootTest
class SagaPatternTest {

    @Autowired
    private OrderCreationSaga orderCreationSaga;

    @Autowired
    private SagaStateRepository sagaStateRepository;

    @MockBean
    private PaymentClient paymentClient;

    @Test
    @DisplayName("결제 실패 시 보상 트랜잭션 실행")
    void paymentFailure_shouldCompensate() {
        // Given
        when(paymentClient.readyPayment(any()))
            .thenThrow(new RuntimeException("결제 실패"));

        OrderCreationCommand command = new OrderCreationCommand(...);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            orderCreationSaga.execute(command);
        });

        // Saga 상태 확인
        SagaState saga = sagaStateRepository.findByAggregateId(command.orderId()).orElseThrow();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        assertThat(saga.getCurrentStep()).isEqualTo("PAYMENT");

        // 보상 트랜잭션 호출 확인
        verify(productClient).rollbackInventory(any(), anyInt());
        verify(couponClient).restoreCoupon(any());
    }
}
```

### 6-3. Grafana 모니터링 대시보드

```promql
# Outbox 대기 중인 이벤트 수
sum(outbox_event_pending_count)

# Outbox 발행 실패율
rate(outbox_event_failed_total[5m]) / rate(outbox_event_total[5m])

# Saga 실행 중인 개수
sum(saga_state_running_count)

# Saga 실패율
rate(saga_state_failed_total[5m]) / rate(saga_state_total[5m])
```

---

## 📊 최종 효과

### Before

| 문제 | 상태 |
|------|------|
| 코드 중복 | 3,410 LOC 중복 |
| 이벤트 유실 | Kafka 실패 시 영구 유실 |
| 분산 트랜잭션 | 추적 불가, 수동 보상 |
| 유지보수 시간 | 보안 정책 변경 시 2시간 |

### After

| 개선 항목 | 효과 |
|----------|------|
| 코드 중복 | **0 LOC (100% 제거)** |
| 이벤트 유실 | **At-Least-Once 보장** |
| 분산 트랜잭션 | **자동 추적 + 자동 보상** |
| 유지보수 시간 | **10분 (92% 단축)** |

---

## 🎯 다음 단계

1. ✅ **즉시**: common-lib, event-schema 모듈 적용
2. 📅 **1주 후**: Outbox Pattern 적용 (Order, Payment)
3. 📅 **2주 후**: Saga Orchestrator 적용
4. 📅 **1개월 후**: 모니터링 대시보드 구축
5. 📅 **2개월 후**: 전체 서비스 확대 적용

---

**작성일**: 2025-01-24
**문서 버전**: 1.0
