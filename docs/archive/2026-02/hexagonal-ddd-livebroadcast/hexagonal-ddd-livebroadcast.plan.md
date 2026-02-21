# Plan: LiveBroadcast 서비스 헥사고날 아키텍처 + DDD 마이그레이션

## 개요

Order, Payment, Coupon, User 서비스에서 검증된 Hexagonal Architecture + DDD 패턴을 LiveBroadcast 서비스에 적용한다.

- **Feature ID**: hexagonal-ddd-livebroadcast
- **작성일**: 2026-02-15
- **우선순위**: High
- **근거 계획**: hexagonal-ddd-domain-services.plan.md (Phase 3)

---

## 현재 상태 (As-Is)

### 아키텍처 분류: 전통적 계층형 아키텍처 (Traditional Layered)

```
Presentation (Controller)
    ↓ 직접 의존
Application (Service + Validator + Manager)
    ↓ 직접 의존
Domain (JPA Entity + Repository Interface)
    ↓ 직접 구현
Infrastructure (JpaRepository, Feign Client, Redis, WebSocket)
```

### 도메인 모델 현황

| 클래스 | 테이블 | 문제점 |
|--------|--------|--------|
| `LiveBroadcast.java` | `p_live_broadcast` | `@Entity`, `@Table` 포함 — 순수 도메인 아님 |
| `BroadcastProduct.java` | `p_broadcast_product` | `@Entity`, `@Table` 포함 |
| `BroadcastSubscription.java` | `p_broadcast_subscriptions` | `@Entity`, `@Table` 포함 |

### 서비스 현황

| 서비스 | 주요 역할 | 외부 의존 |
|--------|----------|----------|
| `LiveBroadcastService` | 방송 CRUD, 검색 | CompanyValidator → CompanyClient |
| `BroadcastProductService` | 방송-상품 연결/해제 | ProductValidator → ProductClient |
| `BroadcastSubscriptionService` | 구독, 알림 등록 | NotificationValidator → NotificationClient |

### Repository 현황

| 인터페이스 위치 | 구현체 위치 | 문제점 |
|----------------|-----------|--------|
| `domain/repository/` | `infrastructure/repository/` (JPA) | 도메인 인터페이스가 JPA 무관 ✅ |
| `domain/repository/query/` | `domain/repository/query/` (QueryDSL Impl) | **구현체가 domain 레이어에 위치** ❌ |

### Port/UseCase 현황

```
Port 인터페이스: 없음 (0개)
UseCase 인터페이스: 없음 (0개)
Adapter 클래스: 없음 (0개)
```

### 외부 연동

| 연동 방식 | 대상 | 용도 |
|----------|------|------|
| Feign (동기) | ProductClient | 상품 조회, 방송-상품 연결 검증 |
| Feign (동기) | CompanyClient | 회사 존재/활성 상태 확인 |
| Feign (동기) | NotificationClient | 방송 알림 등록/해제 |
| Redis | `LIVE_VIEWERS:{id}` Set | 실시간 시청자 수 추적 |
| WebSocket | `/ws/chat` | 시청자 연결 관리 |
| Scheduler | 매분 cron | 방송 상태 자동 전환 (SCHEDULED→LIVE→ENDED) |
| **Kafka: 없음** | - | 현재 이벤트 발행 없음 |

---

## 목표 (To-Be)

Order 서비스에서 검증된 패턴 동일 적용:

```
도메인 모델 (순수 Java, JPA 없음)
    ↕ Port 인터페이스 (UseCase / Repository / External)
Application Service (UseCase 구현체, Port만 주입)
    ↕ Adapter
Infrastructure (JPA / Feign / Redis / WebSocket)
```

### 목표 패키지 구조

```
livebroadcast/src/main/java/com/live_commerce/livebroadcast/
├── domain/
│   ├── model/                    # 순수 Java (JPA 없음)
│   │   ├── LiveBroadcast.java    # @Entity 제거, factory/reconstitute 메서드
│   │   ├── BroadcastProduct.java
│   │   ├── BroadcastSubscription.java
│   │   ├── BroadcastStatus.java  # 기존 Enum 유지
│   │   └── vo/                   # Value Objects
│   │       ├── BroadcastName.java
│   │       └── ViewerCount.java  (선택)
│   ├── port/
│   │   ├── in/                   # UseCase 인터페이스 (Inbound Ports)
│   │   │   ├── CreateBroadcastUseCase.java
│   │   │   ├── UpdateBroadcastUseCase.java
│   │   │   ├── DeleteBroadcastUseCase.java
│   │   │   ├── GetBroadcastUseCase.java
│   │   │   ├── SearchBroadcastUseCase.java
│   │   │   ├── ConnectBroadcastProductUseCase.java
│   │   │   ├── DisconnectBroadcastProductUseCase.java
│   │   │   ├── GetBroadcastProductsUseCase.java
│   │   │   ├── SubscribeBroadcastUseCase.java
│   │   │   ├── UnsubscribeBroadcastUseCase.java
│   │   │   └── RegisterBroadcastAlarmUseCase.java
│   │   └── out/                  # Repository / External Port 인터페이스 (Outbound Ports)
│   │       ├── LiveBroadcastRepositoryPort.java
│   │       ├── BroadcastProductRepositoryPort.java
│   │       ├── BroadcastSubscriptionRepositoryPort.java
│   │       ├── BroadcastQueryPort.java       # QueryDSL 기반 검색/조회
│   │       ├── ExternalProductPort.java      # 상품 서비스 추상화
│   │       ├── ExternalCompanyPort.java      # 회사 서비스 추상화
│   │       └── BroadcastAlarmPort.java       # 알림 서비스 추상화
│   └── exception/
│       └── LiveBroadcastException.java       # 기존 유지
├── application/
│   ├── service/                  # UseCase 구현체 (Port만 주입)
│   │   ├── CreateBroadcastService.java
│   │   ├── UpdateBroadcastService.java
│   │   ├── DeleteBroadcastService.java
│   │   ├── GetBroadcastService.java
│   │   ├── SearchBroadcastService.java
│   │   ├── ConnectBroadcastProductService.java
│   │   ├── DisconnectBroadcastProductService.java
│   │   ├── GetBroadcastProductsService.java
│   │   ├── SubscribeBroadcastService.java
│   │   ├── UnsubscribeBroadcastService.java
│   │   └── RegisterBroadcastAlarmService.java
│   └── dto/
│       ├── command/              # UseCase 입력 커맨드
│       │   ├── CreateBroadcastCommand.java
│       │   ├── UpdateBroadcastCommand.java
│       │   └── ...
│       └── result/               # UseCase 출력 결과
│           ├── LiveBroadcastResult.java
│           ├── BroadcastProductResult.java
│           └── ...
├── adapter/
│   ├── in/
│   │   └── web/                  # REST Controller (기존 controller/ 유지 or 이동)
│   └── out/
│       ├── persistence/          # JPA 어댑터
│       │   ├── entity/           # JpaEntity (분리)
│       │   │   ├── LiveBroadcastJpaEntity.java
│       │   │   ├── BroadcastProductJpaEntity.java
│       │   │   └── BroadcastSubscriptionJpaEntity.java
│       │   ├── repository/       # Spring Data JPA 인터페이스
│       │   │   ├── LiveBroadcastJpaRepository.java
│       │   │   ├── BroadcastProductJpaRepository.java
│       │   │   └── BroadcastSubscriptionJpaRepository.java
│       │   ├── adapter/          # Port 구현체
│       │   │   ├── LiveBroadcastPersistenceAdapter.java
│       │   │   ├── BroadcastProductPersistenceAdapter.java
│       │   │   └── BroadcastSubscriptionPersistenceAdapter.java
│       │   └── query/            # QueryDSL (domain에서 이동)
│       │       ├── BroadcastQueryAdapter.java  (implements BroadcastQueryPort)
│       │       └── BroadcastProductQueryAdapter.java
│       └── client/               # Feign 어댑터
│           ├── ProductFeignAdapter.java   (implements ExternalProductPort)
│           ├── CompanyFeignAdapter.java   (implements ExternalCompanyPort)
│           └── NotificationFeignAdapter.java (implements BroadcastAlarmPort)
├── infrastructure/               # 기술 설정 (변경 최소)
│   ├── client/                   # 기존 Feign 인터페이스 유지
│   ├── config/                   # 기존 설정 유지
│   ├── security/                 # 기존 보안 설정 유지
│   └── websocket/                # 기존 WebSocket 유지
└── presentation/                 # 기존 Controller (UseCase 주입으로 전환)
    └── controller/
```

---

## 마이그레이션 범위 및 전략

### 포함 범위 (In-Scope)

1. **도메인 모델 분리**: 3개 Entity → 순수 도메인 + JpaEntity 분리
2. **Port 인터페이스 생성**: 11개 UseCase (In) + 7개 Repository/External (Out)
3. **Application Service 분리**: 3개 서비스 → 11개 UseCase 구현체
4. **Adapter 구현**: 3개 Persistence, 2개 Query, 3개 Client Feign Adapter
5. **QueryDSL 이동**: `domain/repository/query/` → `adapter/out/persistence/query/`
6. **Controller 전환**: UseCase 인터페이스 주입 (기존 서비스 제거)
7. **기존 서비스 @Deprecated 처리**: 레거시 안정화 후 제거

### 제외 범위 (Out-of-Scope)

- WebSocket 구조 변경 (ViewerWebSocketHandler — 기존 유지)
- Scheduler 구조 변경 (LiveBroadcastStatusScheduler — 기존 유지)
- Kafka 이벤트 발행 추가 (별도 피처로 계획)
- 기존 Redis 사용 패턴 변경

### 점진적 마이그레이션 전략

Order 서비스에서 검증된 방식 동일 적용:

1. 신규 Port/UseCase/Adapter를 기존 코드 옆에 추가
2. Controller를 UseCase 인터페이스로 전환
3. 기존 서비스에 `@Deprecated` 처리
4. 빌드 및 테스트 통과 확인

---

## 핵심 설계 결정사항

### 1. 도메인 모델 분리 방식

기존 `LiveBroadcast.java` (@Entity) → 두 객체로 분리:
- `LiveBroadcast.java` (순수 도메인, JPA 없음) — `create()`, `update()`, `reconstitute()` factory 메서드
- `LiveBroadcastJpaEntity.java` (JPA 엔티티) — `adapter/out/persistence/entity/`

### 2. Validator 처리

기존 `Validator` 클래스들은 서비스 로직 내부로 흡수:
- `CompanyValidator` → `CreateBroadcastService` 내에서 `ExternalCompanyPort` 사용
- `ProductValidator` → `ConnectBroadcastProductService` 내에서 `ExternalProductPort` 사용
- `NotificationValidator` → `RegisterBroadcastAlarmService` 내에서 `BroadcastAlarmPort` 사용
- `LiveBroadcastValidator` → 각 서비스에서 `LiveBroadcastRepositoryPort` 직접 사용

### 3. QueryDSL 처리

기존 `domain/repository/query/` 인터페이스 + 구현체 → 분리:
- 인터페이스: `domain/port/out/BroadcastQueryPort.java`
- 구현체: `adapter/out/persistence/query/BroadcastQueryAdapter.java`

### 4. BroadcastAlarmManager 처리

기존 `BroadcastAlarmManager` (NotificationClient 래퍼) → `BroadcastAlarmPort` 인터페이스로 추상화:
- Port: `domain/port/out/BroadcastAlarmPort.java`
- Adapter: `adapter/out/client/NotificationFeignAdapter.java` (implements BroadcastAlarmPort)

---

## 위험요소 및 대응

| 위험 | 내용 | 대응 |
|------|------|------|
| QueryDSL Q-class 재생성 | JpaEntity 분리 시 QClass 변경 필요 | Order 패턴 동일 적용 (JpaEntity 기반 Q-class) |
| BaseEntity 상속 | 도메인 모델이 BaseEntity를 상속 → 분리 필요 | 도메인에 BaseEntity 필드 직접 포함, JpaEntity만 @MappedSuperclass 사용 |
| Validator 클래스 제거 | 기존 테스트가 Validator를 Mock으로 사용 | 테스트를 UseCase 레벨로 리팩토링 |
| 3개 외부 Feign 호출 | Order보다 많은 외부 서비스 의존 | 각각 Port 인터페이스로 추상화 |
| 테스트 최소 (3개 파일) | 기존 테스트 커버리지 낮음 | 마이그레이션 후 새 UseCase 기반 테스트 추가 |

---

## 완료 기준

- [ ] 모든 도메인 모델에서 `import jakarta.persistence.*` 없음
- [ ] 11개 UseCase 인터페이스가 `domain/port/in/` 위치
- [ ] 7개 Outbound Port 인터페이스가 `domain/port/out/` 위치
- [ ] 11개 Application Service가 Port 인터페이스만 주입 (구현체 직접 의존 없음)
- [ ] 3개 PersistenceAdapter + 3개 FeignAdapter 구현
- [ ] QueryDSL 구현체가 `adapter/out/persistence/query/` 위치
- [ ] 기존 Controller가 UseCase 인터페이스 사용
- [ ] 기존 서비스 `@Deprecated` 처리
- [ ] `./gradlew :livebroadcast:compileJava` BUILD SUCCESSFUL
- [ ] `./gradlew :livebroadcast:test` BUILD SUCCESSFUL
- [ ] Gap Analysis Match Rate ≥ 90%

---

## 구현 순서 (예상)

1. Domain 모델 분리 (LiveBroadcast, BroadcastProduct, BroadcastSubscription)
2. Outbound Port 인터페이스 생성 (7개)
3. Inbound UseCase 인터페이스 생성 (11개)
4. Result DTO 생성 (LiveBroadcastResult, BroadcastProductResult, etc.)
5. Command DTO 생성 (CreateBroadcastCommand, etc.)
6. PersistenceAdapter 구현 (3개 — LiveBroadcast, BroadcastProduct, BroadcastSubscription)
7. BroadcastQueryAdapter 구현 (QueryDSL 이동)
8. FeignAdapter 구현 (ProductFeignAdapter, CompanyFeignAdapter, NotificationFeignAdapter)
9. Application Service 구현 (11개 UseCase 구현체)
10. Controller 전환 (UseCase 주입)
11. 기존 서비스/Validator @Deprecated 처리
12. 빌드 검증

---

## 참고 자료

- Order 서비스 헥사고날 패턴: `order/src/main/java/com/live_commerce/order/`
- 기존 계획: `docs/01-plan/features/hexagonal-ddd-domain-services.plan.md`
- 아카이브된 완료 사례: `docs/archive/2026-02/hexagonal-ddd-order/`
