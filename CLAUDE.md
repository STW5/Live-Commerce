# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Live Commerce Platform - MSA-based live streaming e-commerce system built with Spring Boot 3.4.4, Java 17, and Spring Cloud. Supports real-time broadcasting, chat, product sales, payments, and notifications through event-driven microservices.

## Service Architecture

### Microservices (11 services + 3 infrastructure)

**Domain Services:**
- **user** (19120) - User management, authentication, JWT generation
- **product** (19070) - Product catalog, inventory management with Redisson distributed locks
- **order** (19030) - Order orchestration, Saga coordinator
- **payment** (19080) - Payment processing (KakaoPay integration), distributed lock for payment operations
- **coupon** (19050) - Coupon issuance, validation, Redis-based expiration
- **livebroadcast** (19060) - Live streaming management, broadcast scheduling
- **notification** (19040) - Real-time notifications (Slack, Google SMTP)
- **chat** (19050) - WebSocket-based real-time chat with Redis Pub/Sub
- **company** (19110) - Company/seller management
- **ai** (19100) - Gemini API integration for chat summarization

**Infrastructure Services:**
- **gateway** (19091) - Spring Cloud Gateway, JWT validation, routing
- **eureka** (19090) - Service discovery registry
- **config** (18080) - Spring Cloud Config Server

### Service Ports & URLs

All services accessed through Gateway (19091):
- Gateway: `http://localhost:19091`
- Eureka Dashboard: `http://localhost:19090`
- Kafka UI: `http://localhost:8080`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000` (admin/admin)
- Zipkin: `http://localhost:9411`

## Common Development Commands

### Building & Running

```bash
# Build all services
./gradlew build

# Build specific service
./gradlew :order:build

# Run tests for all services
./gradlew test

# Run tests for specific service
./gradlew :order:test

# Clean build artifacts
./gradlew clean

# Build JAR for specific service
./gradlew :order:bootJar
```

### Docker Operations

```bash
# Start infrastructure (postgres, redis, kafka, monitoring)
docker-compose up -d postgres redis kafka zookeeper prometheus grafana

# Start all services
docker-compose up --build

# Stop all services
docker-compose down

# View logs for specific service
docker-compose logs -f order

# Rebuild specific service
docker-compose up --build order
```

### Running Individual Services (Development)

Services use Spring Cloud Config Server, so start infrastructure first:

```bash
# 1. Start infrastructure services
docker-compose up -d postgres redis kafka zookeeper eureka config

# 2. Run specific service from IDE or:
./gradlew :order:bootRun

# Note: Each service connects to config server at localhost:18080
```

### Database Operations

```bash
# Connect to PostgreSQL
docker exec -it live-commerce-db psql -U postgres -d livecommerce

# List all schemas
\dn

# Connect to specific schema
SET search_path TO orders;

# List tables in current schema
\dt
```

## Architectural Patterns

### 1. Event-Driven Saga Pattern (Distributed Transactions)

**Order → Product → Payment Flow:**

```
User creates order
    → Order Service: Creates order, publishes "inventory-decrease"
    → Product Service: Consumes event, applies Redisson lock, decreases stock, publishes "inventory-decreased"
    → Order Service: Consumes "inventory-decreased", initiates payment via Feign
    → Payment Service: Processes payment (KakaoPay), publishes "payment-completed"
    → Order Service: Consumes "payment-completed", finalizes order

Failure Path:
    Payment fails → "payment-failed" → "order-failed" → "inventory-rollback" → Stock restored
```

**Critical Implementation Detail:** All state-changing operations use `@Transactional` with Kafka manual acknowledgment (`MANUAL_IMMEDIATE`) to ensure exactly-once semantics.

### 2. Kafka Topics & Event Flow

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `payment-completed` | Payment | Order | Confirm successful payment |
| `payment-failed` | Payment | Order | Handle payment failure |
| `order-failed` | Order | Payment | Trigger refund compensation |
| `inventory-decrease` | Order | Product | Request stock reduction |
| `inventory-decreased` | Product | Order | Confirm stock reduction |
| `inventory-rollback` | Order | Product | Restore stock on failure |
| `inventory-sold-out` | Product | All | Broadcast out-of-stock |
| `first-join-coupon` | User | Coupon | Issue signup coupon |
| `coupon-used` | Order | Coupon | Mark coupon as consumed |
| `notification-created` | Various | Notification | Trigger notifications |

### 3. Distributed Locking with Redisson

**Implementation Pattern:**

```java
@DistributedLock(key = "#dto.orderId", waitTime = 5L, leaseTime = 3L)
@Transactional
public PaymentReadyResponseDto readyPayment(RequestUserDetails user, PaymentReadyRequestDto dto) {
    // Prevents concurrent payment attempts for same order
}
```

**Lock Prefix Conventions:**
- Payment: `payment:lock:{orderId}`
- Inventory: `LOCK:{productId}`

**Critical Services Using Locks:**
- Product Service: Inventory decrease operations
- Payment Service: Payment preparation and approval

### 4. Authentication & Authorization

**Gateway-Level (First Checkpoint):**
1. Validates JWT token signature
2. Extracts claims: `userId`, `username`, `role`
3. Injects custom headers: `X-User-Id`, `X-User-Username`, `X-User-Role`

**Service-Level (All Microservices):**
1. `AuthenticationFilter` reads custom headers
2. Reconstructs `RequestUserDetails` from headers
3. Populates `SecurityContextHolder` for method-level security

**Feign Client Propagation:**
- `FeignClientInterceptor` automatically propagates headers to downstream services
- All Feign calls include `Authorization: Bearer {token}` + custom headers

**Security Exclusions:**
- Swagger UI: `/swagger-ui/**`, `/v3/api-docs/**`
- Actuator: `/actuator/**` (public health checks)
- Auth endpoints: `/api/v1/auth/**`, `/api/v2/auth/**`

### 5. Service Communication Patterns

**Synchronous (Feign):**
- User validation, product lookups, coupon checks
- Configured with request interceptors for auth propagation
- Example: `OrderService` → `ProductClient.getProduct()`

**Asynchronous (Kafka):**
- State transitions, event notifications, compensation logic
- Manual ACK mode for reliability
- Example: `PaymentService` → `payment-completed` event

### 6. Database Schema Isolation

Each service has its own PostgreSQL schema:
- `users` - User service
- `orders` - Order service
- `products` - Product service
- `payments` - Payment service
- `coupons` - Coupon service
- `broadcasts` - Livebroadcast service
- `companies` - Company service
- `notifications` - Notification service

**No direct cross-schema queries** - all inter-service data access via Feign or Kafka.

### 7. Redis Usage Patterns

**Caching (Product Service):**
- Product details with TTL
- Top 10 popular products ranking

**Distributed Locks (Product, Payment):**
- Redisson RLock with wait/lease time configuration
- AOP-based lock management via `@DistributedLock`

**Pub/Sub (Chat Service):**
- Real-time chat message broadcasting
- Channel: `chat-channel`

**TTL-Based State Management (Coupon Service):**
- Coupon expiration tracking
- Issued coupon status

## Configuration Management

### Spring Cloud Config Server

Services load config from centralized config server:

```yaml
spring:
  config:
    import: "configserver:http://localhost:18080"
  application:
    name: {service-name}
  profiles:
    active: dev  # or prod
```

**Config Repository:** `config-repo/` (separate repository or directory)

**Profile-Specific Files:**
- `order-dev.yml` - Development config for order service
- `order-prod.yml` - Production config for order service

### Environment Variables (Docker)

Services use `.env.prod` files when running in Docker:
- Database connection strings
- Kafka bootstrap servers
- Redis host/port
- External API keys (KakaoPay, Gemini, Slack)

## Package Structure (All Services)

```
src/main/java/com/live_commerce/{service}/
├── presentation/
│   └── controller/         # REST controllers, @Valid request validation
├── application/
│   ├── service/           # Business logic, orchestration
│   ├── dto/              # Request/Response DTOs (records)
│   └── exception/        # Custom domain exceptions
├── domain/
│   ├── model/            # JPA entities with auditing
│   ├── repository/       # JPA Repository + QueryDSL
│   └── event/            # Domain event records
├── infrastructure/
│   ├── client/           # Feign clients with fallbacks
│   ├── config/           # Spring configurations
│   ├── kafka/            # Kafka producers/consumers
│   ├── security/         # JWT utils, auth filters
│   ├── redis/            # Redis operations, Redisson config
│   └── exception/        # Global exception handlers
└── {Service}Application.java
```

## Key Integration Points

### Order Creation Flow (Most Complex)

```java
// OrderCreateService orchestrates multiple services:
1. broadcastClient.getBroadcast(broadcastId)         // Validate broadcast is live
2. productClient.getProduct(productId)               // Get product details
3. productClient.checkOrderableInventory(quantity)   // Check stock availability
4. couponClient.getIssuedCoupons()                   // Get user coupons
5. orderRepository.save(order)                       // Persist order
6. inventoryEventProducer.send(inventoryEvent)       // Kafka: Request stock decrease
7. Wait for Kafka: inventory-decreased               // Product confirms decrease
8. paymentClient.readyPayment(paymentDto)            // Initiate payment
9. Wait for Kafka: payment-completed                 // Payment confirms success
```

### Compensation (Rollback) Logic

**Payment Failure:**
```
PaymentService → payment-failed (Kafka)
              → order-failed (Kafka)
              → OrderService updates status to FAILED
              → inventory-rollback (Kafka)
              → ProductService restores stock
```

## Testing

### Unit Tests

```bash
# Run all tests
./gradlew test

# Run tests for specific service
./gradlew :order:test

# Run specific test class
./gradlew :order:test --tests OrderCreateServiceTest
```

**Test Resources:** Each service has `application-test.yml` with H2 or testcontainers config.

### Integration Testing

Services use `@SpringBootTest` with:
- Embedded Kafka (spring-kafka-test)
- TestContainers for PostgreSQL
- Embedded Redis

### API Testing

**Swagger UI (per service):**
- Gateway: `http://localhost:19091/swagger-ui.html`
- Order: `http://localhost:19030/swagger-ui.html`

**Example API Calls:**

```bash
# Register user
POST http://localhost:19091/api/v2/auth/signup
Content-Type: application/json
{
  "email": "test@example.com",
  "password": "password123",
  "username": "testuser",
  "role": "ROLE_CUSTOMER"
}

# Create order (requires JWT)
POST http://localhost:19091/api/v1/orders
Authorization: Bearer {jwt_token}
Content-Type: application/json
{
  "broadcastId": "uuid",
  "productId": "uuid",
  "orderQuantity": 2,
  "couponId": "uuid"
}
```

## Monitoring & Observability

### Prometheus Metrics

All services expose `/actuator/prometheus` endpoint.

**Key Metrics:**
- JVM memory, threads, GC
- HTTP request rate, latency
- Kafka consumer lag
- Database connection pool stats

### Grafana Dashboards

Import pre-configured dashboards for:
- Service health overview
- Kafka topic monitoring
- Database connection pools
- JVM performance

### Distributed Tracing (Zipkin)

All HTTP and Kafka calls traced with correlation IDs.

**View traces:** `http://localhost:9411`

## Common Issues & Solutions

### Issue: JWT authentication fails in downstream services

**Cause:** Custom headers not propagated via Feign.

**Solution:** Verify `FeignClientInterceptor` is configured in all services calling other services.

### Issue: Kafka consumer duplicate processing

**Cause:** Consumer offset not committed, or idempotency key missing.

**Solution:**
- Use `@KafkaListener` with `ackMode = MANUAL_IMMEDIATE`
- Implement idempotency checks using event keys or database constraints

### Issue: Distributed lock timeout

**Cause:** High contention or long-running transaction holding lock.

**Solution:**
- Increase `waitTime` in `@DistributedLock` annotation
- Optimize locked code section
- Check for deadlocks in database transactions

### Issue: Config server connection failure on startup

**Cause:** Config server not running before service starts.

**Solution:** Start infrastructure services first:
```bash
docker-compose up -d config eureka
# Wait 10 seconds, then start application services
```

### Issue: Inventory inconsistency after failure

**Cause:** Compensation event (`inventory-rollback`) not processed.

**Solution:**
- Check Kafka consumer logs in Product service
- Verify Redisson lock is released (check Redis keys)
- Manually trigger rollback via admin endpoint if needed

## External Integrations

### KakaoPay (Payment Service)

**Endpoints:**
- Ready: `/v1/payment/ready`
- Approve: `/v1/payment/approve`
- Cancel: `/v1/payment/cancel`

**Configuration:** `payment-{profile}.yml` contains `cid`, `secret`, `admin-key`.

### Google Gemini (AI Service)

**Model:** `gemini-2.0-flash-exp`

**Purpose:** Chat summarization and analysis.

**Configuration:** `ai-{profile}.yml` contains `gemini.api-key`.

### Slack Notifications (Notification Service)

**Webhook URL:** Configured in `.env` or application config.

**Triggers:** Broadcast start reminders, order failures, system alerts.

## Code Quality & Conventions

### Lombok Annotations

- `@RequiredArgsConstructor` for dependency injection (preferred over `@Autowired`)
- `@Slf4j` for logging
- `@Getter`/`@Setter` on entities (avoid on DTOs - use records)
- `@Builder` for complex object construction

### DTOs as Records

All request/response DTOs are Java records:

```java
public record OrderCreateRequest(
    @NotNull UUID broadcastId,
    @NotNull UUID productId,
    @Min(1) int orderQuantity,
    UUID couponId
) {
    public Order toOrder(int productTotalPrice, int finalPaidPrice, UUID userId) {
        // Conversion logic
    }
}
```

### Exception Handling

**Pattern:** Domain-specific exceptions with static factory methods:

```java
public class OrderException extends RuntimeException {
    public static OrderException forOrderNotFound(UUID orderId) {
        return new OrderException("Order not found: " + orderId);
    }
}
```

**Global Handler:** `@RestControllerAdvice` in `infrastructure.exception` package.

### QueryDSL for Complex Queries

**Generated Q-classes location:** `src/main/generated/`

**Usage:**
```java
public interface OrderRepositoryCustom {
    List<Order> findByUserAndStatus(UUID userId, OrderStatus status);
}

public class OrderRepositoryImpl implements OrderRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    @Override
    public List<Order> findByUserAndStatus(UUID userId, OrderStatus status) {
        QOrder order = QOrder.order;
        return queryFactory.selectFrom(order)
            .where(order.userId.eq(userId).and(order.status.eq(status)))
            .fetch();
    }
}
```

## Deployment

### Docker Build Process

Each service has a Dockerfile:

```dockerfile
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY build/libs/{service}-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
EXPOSE {port}
```

**Build Steps:**
1. `./gradlew :{service}:bootJar` - Creates executable JAR
2. `docker build -t {service}:latest ./{service}` - Builds image
3. `docker-compose up -d {service}` - Runs container

### AWS Deployment (ECS)

**Services deployed to AWS:**
- ECR: Docker image registry
- ECS: Container orchestration
- RDS: PostgreSQL database
- ElastiCache: Redis cluster

**CI/CD:** GitHub Actions workflows in `.github/workflows/`

## Additional Notes

### WebSocket Chat (Chat Service)

**Endpoint:** `ws://localhost:19050/ws/chat`

**Protocol:** Custom HandshakeInterceptor (not STOMP) for JWT authentication.

**Message Flow:**
1. Client connects with JWT token
2. Messages published to Redis Pub/Sub
3. All connected clients receive via WebSocket

### Broadcast Notification Trigger (Livebroadcast Service)

**Feature:** 10-minute pre-broadcast notification.

**Implementation:**
- Scheduled task checks upcoming broadcasts
- Publishes `notification-created` Kafka event
- Notification service sends to subscribed users via Slack/Email

### Coupon Auto-Issuance (User + Coupon Services)

**Flow:**
1. User signs up → User service publishes `first-join-coupon` event
2. Coupon service consumes event → Issues welcome coupon
3. Coupon stored in Redis with TTL + PostgreSQL

### Inventory Sold-Out Broadcasting (Product Service)

**Trigger:** When `availableQuantity` reaches 0 after decrease operation.

**Event:** `inventory-sold-out` published to Kafka.

**Consumers:** All services can listen and react (e.g., hide product from UI, stop accepting orders).
