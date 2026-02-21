# Design: LiveBroadcast 서비스 헥사고날 아키텍처 + DDD 마이그레이션

## 개요

- **Feature ID**: hexagonal-ddd-livebroadcast
- **Plan 참조**: `docs/01-plan/features/hexagonal-ddd-livebroadcast.plan.md`
- **참고 패턴**: Order 서비스 (`order/src/main/java/com/live_commerce/order/`)
- **작성일**: 2026-02-15

---

## 1. 도메인 모델 설계

### 1.1 BaseEntity 분리

기존 `BaseEntity`는 JPA 어노테이션(`@MappedSuperclass`, `@EntityListeners`, `@CreatedDate` 등)을 포함하고 있어 분리가 필요하다.

**도메인 기본 클래스** (`domain/model/BaseEntity.java`) — JPA 없는 순수 Java:

```java
// domain/model/BaseEntity.java
public abstract class BaseEntity {
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
    private UUID deletedBy;
    private boolean deletedStatus;

    protected BaseEntity() {
        this.deletedStatus = false;
    }

    public void delete(UUID deletedById) {
        this.deletedStatus = true;
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedById;
    }
    // getters
}
```

**JPA 기본 클래스** (`adapter/out/persistence/entity/BaseJpaEntity.java`) — JPA 어노테이션 포함:

```java
// adapter/out/persistence/entity/BaseJpaEntity.java
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseJpaEntity {
    @CreatedDate @Column(updatable = false)
    private LocalDateTime createdAt;
    @CreatedBy @Column(updatable = false)
    private String createdBy;
    @LastModifiedDate private LocalDateTime updatedAt;
    @LastModifiedBy private String updatedBy;
    private LocalDateTime deletedAt;
    private UUID deletedBy;
    @Column(nullable = false) private boolean deletedStatus;

    protected BaseJpaEntity() { this.deletedStatus = false; }

    public void delete(UUID deletedById) {
        this.deletedStatus = true;
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedById;
    }
}
```

---

### 1.2 LiveBroadcast 도메인 모델

**파일**: `domain/model/LiveBroadcast.java`

```java
// JPA 없는 순수 도메인 모델
public class LiveBroadcast extends BaseEntity {
    private UUID liveBroadcastId;
    private String broadcastName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BroadcastStatus broadcastStatus;
    private UUID hostId;
    private UUID companyId;
    private Integer totalViewerCount;

    // 신규 생성용 팩토리
    public static LiveBroadcast create(String broadcastName, LocalDateTime startTime,
            LocalDateTime endTime, UUID hostId, UUID companyId) {
        LiveBroadcast b = new LiveBroadcast();
        b.liveBroadcastId = UUID.randomUUID();
        b.broadcastName = broadcastName;
        b.startTime = startTime;
        b.endTime = endTime;
        b.broadcastStatus = BroadcastStatus.SCHEDULED;
        b.hostId = hostId;
        b.companyId = companyId;
        b.totalViewerCount = 0;
        return b;
    }

    // DB 조회 후 복원용 팩토리
    public static LiveBroadcast reconstitute(UUID id, String broadcastName, LocalDateTime startTime,
            LocalDateTime endTime, BroadcastStatus status, UUID hostId, UUID companyId,
            Integer totalViewerCount, LocalDateTime createdAt, String createdBy,
            LocalDateTime updatedAt, String updatedBy, LocalDateTime deletedAt,
            UUID deletedBy, boolean deletedStatus) { ... }

    // 도메인 메서드 (application DTO 의존 없음)
    public void update(String broadcastName, LocalDateTime startTime, LocalDateTime endTime,
            BroadcastStatus broadcastStatus) {
        if (broadcastName != null) this.broadcastName = broadcastName;
        if (startTime != null) this.startTime = startTime;
        if (endTime != null) this.endTime = endTime;
        if (broadcastStatus != null) this.broadcastStatus = broadcastStatus;
    }

    public void updateStatus(BroadcastStatus newStatus) {
        this.broadcastStatus = newStatus;
    }
}
```

**핵심 변경점**:
- `@Entity`, `@Table`, `@Column` 등 JPA 어노테이션 제거
- `update()` 메서드에서 `LiveBroadcastUpdateRequestDto` 의존 제거 → 파라미터로 직접 받음
- `reconstitute()` 팩토리 메서드 추가 (DB 복원용)
- `BaseEntity` 확장 (순수 Java 버전)

---

### 1.3 BroadcastProduct 도메인 모델

**파일**: `domain/model/BroadcastProduct.java`

```java
public class BroadcastProduct extends BaseEntity {
    private UUID broadcastProductId;
    private UUID liveBroadcastId;
    private UUID productId;

    public static BroadcastProduct create(UUID liveBroadcastId, UUID productId) {
        BroadcastProduct bp = new BroadcastProduct();
        bp.broadcastProductId = UUID.randomUUID();
        bp.liveBroadcastId = liveBroadcastId;
        bp.productId = productId;
        return bp;
    }

    public static BroadcastProduct reconstitute(UUID id, UUID liveBroadcastId,
            UUID productId, /* BaseEntity fields */) { ... }
}
```

---

### 1.4 BroadcastSubscription 도메인 모델

**파일**: `domain/model/BroadcastSubscription.java`

```java
public class BroadcastSubscription extends BaseEntity {
    private UUID subscriptionId;
    private UUID userId;
    private UUID broadcastId;

    public static BroadcastSubscription create(UUID userId, UUID broadcastId) {
        BroadcastSubscription sub = new BroadcastSubscription();
        sub.subscriptionId = UUID.randomUUID();
        sub.userId = userId;
        sub.broadcastId = broadcastId;
        return sub;
    }

    public static BroadcastSubscription reconstitute(UUID id, UUID userId,
            UUID broadcastId, /* BaseEntity fields */) { ... }
}
```

---

## 2. Port 인터페이스 설계

### 2.1 Inbound Ports (UseCase 인터페이스)

**위치**: `domain/port/in/`

#### 방송 CRUD

```java
// CreateBroadcastUseCase.java
public interface CreateBroadcastUseCase {
    LiveBroadcastResult createBroadcast(CreateBroadcastCommand command, UUID hostId, UUID companyId);
}

// UpdateBroadcastUseCase.java
public interface UpdateBroadcastUseCase {
    LiveBroadcastResult updateBroadcast(UUID broadcastId, UpdateBroadcastCommand command, UUID userId, String role);
}

// DeleteBroadcastUseCase.java
public interface DeleteBroadcastUseCase {
    void deleteBroadcast(UUID broadcastId, UUID userId, String role);
}

// GetBroadcastUseCase.java
public interface GetBroadcastUseCase {
    LiveBroadcastResult getBroadcast(UUID broadcastId);
}

// SearchBroadcastUseCase.java
public interface SearchBroadcastUseCase {
    Page<LiveBroadcastResult> searchBroadcast(String keyword, Pageable pageable);
}
```

#### 방송-상품 연결

```java
// ConnectBroadcastProductUseCase.java
public interface ConnectBroadcastProductUseCase {
    BroadcastProductResult connectProduct(UUID broadcastId, UUID productId, UUID userId, String role);
}

// DisconnectBroadcastProductUseCase.java
public interface DisconnectBroadcastProductUseCase {
    void disconnectProduct(UUID broadcastId, UUID productId, UUID userId, String role);
}

// GetBroadcastProductsUseCase.java
public interface GetBroadcastProductsUseCase {
    Page<BroadcastProductResult> getProducts(UUID broadcastId, Pageable pageable);
}

// CheckBroadcastProductExistsUseCase.java
public interface CheckBroadcastProductExistsUseCase {
    boolean exists(UUID broadcastId, UUID productId);
}
```

#### 구독

```java
// SubscribeBroadcastUseCase.java
public interface SubscribeBroadcastUseCase {
    BroadcastSubscriptionResult subscribe(UUID userId, UUID broadcastId);
}

// UnsubscribeBroadcastUseCase.java
public interface UnsubscribeBroadcastUseCase {
    void unsubscribe(UUID userId, UUID broadcastId);
}

// GetMySubscriptionsUseCase.java
public interface GetMySubscriptionsUseCase {
    List<BroadcastSubscriptionResult> getMySubscriptions(UUID userId);
}

// GetBroadcastSubscribersUseCase.java
public interface GetBroadcastSubscribersUseCase {
    Page<UUID> getSubscribers(UUID broadcastId, Pageable pageable);
}

// RegisterBroadcastAlarmUseCase.java
public interface RegisterBroadcastAlarmUseCase {
    void registerAlarm(UUID userId, UUID broadcastId);
}
```

**총 14개 UseCase 인터페이스**

---

### 2.2 Outbound Ports (Repository / External Port 인터페이스)

**위치**: `domain/port/out/`

#### Repository Ports

```java
// LiveBroadcastRepositoryPort.java
public interface LiveBroadcastRepositoryPort {
    LiveBroadcast save(LiveBroadcast broadcast);
    Optional<LiveBroadcast> findById(UUID broadcastId);
    boolean existsById(UUID broadcastId);
    List<LiveBroadcast> findAllByStatusIn(List<BroadcastStatus> statuses);  // 스케줄러용
    UUID findHostIdByBroadcastId(UUID broadcastId);
    void softDelete(UUID broadcastId, UUID deletedById);
}

// BroadcastProductRepositoryPort.java
public interface BroadcastProductRepositoryPort {
    BroadcastProduct save(BroadcastProduct broadcastProduct);
    Optional<BroadcastProduct> findByBroadcastIdAndProductId(UUID broadcastId, UUID productId);
    boolean existsByBroadcastIdAndProductId(UUID broadcastId, UUID productId);
    void softDelete(UUID broadcastProductId, UUID deletedById);
}

// BroadcastSubscriptionRepositoryPort.java
public interface BroadcastSubscriptionRepositoryPort {
    BroadcastSubscription save(BroadcastSubscription subscription);
    Optional<BroadcastSubscription> findByUserIdAndBroadcastId(UUID userId, UUID broadcastId);
    boolean existsByUserIdAndBroadcastId(UUID userId, UUID broadcastId);
    List<BroadcastSubscription> findAllByUserId(UUID userId);
    Page<UUID> findSubscriberIdsByBroadcastId(UUID broadcastId, Pageable pageable);
    void softDelete(UUID subscriptionId, UUID deletedById);
}
```

#### Query Port (QueryDSL)

```java
// BroadcastQueryPort.java
public interface BroadcastQueryPort {
    Page<LiveBroadcastResult> searchByName(String keyword, Pageable pageable);
    Page<UUID> findProductIdsByBroadcastId(UUID broadcastId, Pageable pageable);
}
```

#### External Service Ports

```java
// ExternalProductPort.java
public interface ExternalProductPort {
    ExternalProductInfo getProduct(UUID productId);
    List<ExternalProductInfo> getProducts(List<UUID> productIds);
}

// ExternalCompanyPort.java
public interface ExternalCompanyPort {
    ExternalCompanyInfo getCompany(UUID companyId);
    boolean isActiveCompany(UUID companyId);
}

// BroadcastAlarmPort.java
public interface BroadcastAlarmPort {
    void registerAlarm(UUID broadcastId, UUID userId, LocalDateTime notifyAt);
    void deleteAlarm(UUID broadcastId);
}
```

**총 7개 Outbound Port 인터페이스** (3개 Repository + 1개 Query + 3개 External)

---

## 3. Result / Command DTO 설계

### 3.1 Result DTOs (`application/dto/result/`)

```java
// LiveBroadcastResult.java
public record LiveBroadcastResult(
    UUID liveBroadcastId,
    String broadcastName,
    LocalDateTime startTime,
    LocalDateTime endTime,
    BroadcastStatus broadcastStatus,
    UUID hostId,
    UUID companyId,
    Integer totalViewerCount
) {
    public static LiveBroadcastResult from(LiveBroadcast broadcast) { ... }
}

// BroadcastProductResult.java
public record BroadcastProductResult(
    UUID broadcastProductId,
    UUID liveBroadcastId,
    UUID productId
) {
    public static BroadcastProductResult from(BroadcastProduct bp) { ... }
}

// BroadcastSubscriptionResult.java
public record BroadcastSubscriptionResult(
    UUID subscriptionId,
    UUID userId,
    UUID broadcastId
) {
    public static BroadcastSubscriptionResult from(BroadcastSubscription sub) { ... }
}

// ExternalProductInfo.java  — 외부 상품 정보 (Port 반환용)
public record ExternalProductInfo(
    UUID productId,
    String productName,
    UUID companyId,
    BigDecimal price
) {}

// ExternalCompanyInfo.java  — 외부 회사 정보 (Port 반환용)
public record ExternalCompanyInfo(
    UUID companyId,
    String companyName,
    boolean active
) {}
```

### 3.2 Command DTOs (`application/dto/command/`)

```java
// CreateBroadcastCommand.java
public record CreateBroadcastCommand(
    String broadcastName,
    LocalDateTime startTime,
    LocalDateTime endTime
) {}

// UpdateBroadcastCommand.java
public record UpdateBroadcastCommand(
    String broadcastName,
    LocalDateTime startTime,
    LocalDateTime endTime,
    BroadcastStatus broadcastStatus
) {}
```

---

## 4. Application Service 설계

**원칙**: Port 인터페이스만 주입, Feign 클라이언트/JPA Repository 직접 의존 없음

### 4.1 방송 CRUD 서비스

```java
// CreateBroadcastService.java
@Service @RequiredArgsConstructor
public class CreateBroadcastService implements CreateBroadcastUseCase {
    private final LiveBroadcastRepositoryPort broadcastRepository;
    private final ExternalCompanyPort companyPort;
    private final BroadcastAlarmPort alarmPort;

    @Override @Transactional
    public LiveBroadcastResult createBroadcast(CreateBroadcastCommand command, UUID hostId, UUID companyId) {
        // 1. 회사 유효성 확인
        ExternalCompanyInfo company = companyPort.getCompany(companyId);
        if (!company.active()) throw new LiveBroadcastException("비활성 회사");

        // 2. 도메인 생성
        LiveBroadcast broadcast = LiveBroadcast.create(
            command.broadcastName(), command.startTime(), command.endTime(), hostId, companyId);

        // 3. 저장
        LiveBroadcast saved = broadcastRepository.save(broadcast);

        // 4. 알림 등록 (방송 시작 20분 전)
        LocalDateTime notifyAt = saved.getStartTime().minusMinutes(20);
        alarmPort.registerAlarm(saved.getLiveBroadcastId(), hostId, notifyAt);

        return LiveBroadcastResult.from(saved);
    }
}

// UpdateBroadcastService.java
@Service @RequiredArgsConstructor
public class UpdateBroadcastService implements UpdateBroadcastUseCase {
    private final LiveBroadcastRepositoryPort broadcastRepository;
    private final BroadcastAlarmPort alarmPort;

    @Override @Transactional
    public LiveBroadcastResult updateBroadcast(UUID broadcastId, UpdateBroadcastCommand command,
            UUID userId, String role) {
        LiveBroadcast broadcast = broadcastRepository.findById(broadcastId)
            .orElseThrow(() -> LiveBroadcastException.notFound(broadcastId));

        // 권한 확인 (MASTER 또는 호스트 본인)
        validateOwnerOrMaster(broadcast, userId, role);

        // 도메인 메서드로 수정
        broadcast.update(command.broadcastName(), command.startTime(),
            command.endTime(), command.broadcastStatus());
        LiveBroadcast saved = broadcastRepository.save(broadcast);

        // 알림 재등록 (startTime 변경 시)
        if (command.startTime() != null) {
            alarmPort.deleteAlarm(broadcastId);
            alarmPort.registerAlarm(broadcastId, userId, saved.getStartTime().minusMinutes(20));
        }
        return LiveBroadcastResult.from(saved);
    }
}

// DeleteBroadcastService.java
@Service @RequiredArgsConstructor
public class DeleteBroadcastService implements DeleteBroadcastUseCase {
    private final LiveBroadcastRepositoryPort broadcastRepository;
    private final BroadcastAlarmPort alarmPort;

    @Override @Transactional
    public void deleteBroadcast(UUID broadcastId, UUID userId, String role) {
        LiveBroadcast broadcast = broadcastRepository.findById(broadcastId)
            .orElseThrow(() -> LiveBroadcastException.notFound(broadcastId));
        validateOwnerOrMaster(broadcast, userId, role);
        broadcastRepository.softDelete(broadcastId, userId);
        alarmPort.deleteAlarm(broadcastId);
    }
}

// GetBroadcastService.java
@Service @RequiredArgsConstructor
public class GetBroadcastService implements GetBroadcastUseCase {
    private final LiveBroadcastRepositoryPort broadcastRepository;

    @Override @Transactional(readOnly = true)
    public LiveBroadcastResult getBroadcast(UUID broadcastId) {
        LiveBroadcast broadcast = broadcastRepository.findById(broadcastId)
            .orElseThrow(() -> LiveBroadcastException.notFound(broadcastId));
        return LiveBroadcastResult.from(broadcast);
    }
}

// SearchBroadcastService.java
@Service @RequiredArgsConstructor
public class SearchBroadcastService implements SearchBroadcastUseCase {
    private final BroadcastQueryPort queryPort;

    @Override @Transactional(readOnly = true)
    public Page<LiveBroadcastResult> searchBroadcast(String keyword, Pageable pageable) {
        // 페이지 사이즈 검증 (10, 30, 50만 허용)
        validatePageSize(pageable.getPageSize());
        return queryPort.searchByName(keyword, pageable);
    }
}
```

### 4.2 방송-상품 연결 서비스

```java
// ConnectBroadcastProductService.java
@Service @RequiredArgsConstructor
public class ConnectBroadcastProductService implements ConnectBroadcastProductUseCase {
    private final LiveBroadcastRepositoryPort broadcastRepository;
    private final BroadcastProductRepositoryPort productRepository;
    private final ExternalProductPort productPort;

    @Override @Transactional
    public BroadcastProductResult connectProduct(UUID broadcastId, UUID productId, UUID userId, String role) {
        LiveBroadcast broadcast = broadcastRepository.findById(broadcastId)
            .orElseThrow(() -> LiveBroadcastException.notFound(broadcastId));
        validateOwnerOrMaster(broadcast, userId, role);

        // 외부 상품 검증 (회사 일치 확인)
        ExternalProductInfo product = productPort.getProduct(productId);
        if (!product.companyId().equals(broadcast.getCompanyId()))
            throw new LiveBroadcastException("방송 회사와 상품 회사가 다릅니다.");

        // 이미 연결됐는지 확인
        if (productRepository.existsByBroadcastIdAndProductId(broadcastId, productId))
            throw new LiveBroadcastException("이미 연결된 상품입니다.");

        BroadcastProduct bp = BroadcastProduct.create(broadcastId, productId);
        return BroadcastProductResult.from(productRepository.save(bp));
    }
}

// GetBroadcastProductsService.java
@Service @RequiredArgsConstructor
public class GetBroadcastProductsService implements GetBroadcastProductsUseCase {
    private final BroadcastQueryPort queryPort;
    private final ExternalProductPort productPort;

    @Override @Transactional(readOnly = true)
    public Page<BroadcastProductResult> getProducts(UUID broadcastId, Pageable pageable) {
        // QueryDSL로 productId 목록 조회
        Page<UUID> productIds = queryPort.findProductIdsByBroadcastId(broadcastId, pageable);
        // 외부 상품 정보 일괄 조회
        List<ExternalProductInfo> products = productPort.getProducts(productIds.getContent());
        // 결합해서 반환
        return buildBroadcastProductResults(productIds, products);
    }
}

// CheckBroadcastProductExistsService.java
@Service @RequiredArgsConstructor
public class CheckBroadcastProductExistsService implements CheckBroadcastProductExistsUseCase {
    private final BroadcastProductRepositoryPort productRepository;

    @Override @Transactional(readOnly = true)
    public boolean exists(UUID broadcastId, UUID productId) {
        return productRepository.existsByBroadcastIdAndProductId(broadcastId, productId);
    }
}
```

### 4.3 구독 서비스

```java
// SubscribeBroadcastService.java
@Service @RequiredArgsConstructor
public class SubscribeBroadcastService implements SubscribeBroadcastUseCase {
    private final BroadcastSubscriptionRepositoryPort subscriptionRepository;
    private final LiveBroadcastRepositoryPort broadcastRepository;

    @Override @Transactional
    public BroadcastSubscriptionResult subscribe(UUID userId, UUID broadcastId) {
        broadcastRepository.findById(broadcastId)
            .orElseThrow(() -> LiveBroadcastException.notFound(broadcastId));
        if (subscriptionRepository.existsByUserIdAndBroadcastId(userId, broadcastId))
            throw new LiveBroadcastException("이미 구독 중입니다.");
        BroadcastSubscription sub = BroadcastSubscription.create(userId, broadcastId);
        return BroadcastSubscriptionResult.from(subscriptionRepository.save(sub));
    }
}

// RegisterBroadcastAlarmService.java
@Service @RequiredArgsConstructor
public class RegisterBroadcastAlarmService implements RegisterBroadcastAlarmUseCase {
    private final LiveBroadcastRepositoryPort broadcastRepository;
    private final BroadcastAlarmPort alarmPort;

    @Override @Transactional
    public void registerAlarm(UUID userId, UUID broadcastId) {
        LiveBroadcast broadcast = broadcastRepository.findById(broadcastId)
            .orElseThrow(() -> LiveBroadcastException.notFound(broadcastId));
        LocalDateTime notifyAt = broadcast.getStartTime().minusMinutes(10);
        alarmPort.registerAlarm(broadcastId, userId, notifyAt);
    }
}
```

---

## 5. Adapter 설계

### 5.1 Persistence Adapter (JPA Entity 분리)

#### JpaEntity 클래스 (`adapter/out/persistence/entity/`)

```java
// LiveBroadcastJpaEntity.java
@Entity @Getter
@Table(name = "p_live_broadcast", schema = "livebroadcast")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveBroadcastJpaEntity extends BaseJpaEntity {
    @Id @UuidGenerator private UUID liveBroadcastId;
    @Column(nullable = false) private String broadcastName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    @Enumerated(EnumType.STRING) private BroadcastStatus broadcastStatus;
    private UUID hostId;
    private UUID companyId;
    private Integer totalViewerCount;

    // 도메인 → JpaEntity 변환
    public static LiveBroadcastJpaEntity fromDomain(LiveBroadcast domain) { ... }

    // JpaEntity → 도메인 변환
    public LiveBroadcast toDomain() {
        return LiveBroadcast.reconstitute(
            liveBroadcastId, broadcastName, startTime, endTime,
            broadcastStatus, hostId, companyId, totalViewerCount,
            getCreatedAt(), getCreatedBy(), getUpdatedAt(), getUpdatedBy(),
            getDeletedAt(), getDeletedBy(), isDeletedStatus());
    }
}

// BroadcastProductJpaEntity.java — 유사 패턴
// BroadcastSubscriptionJpaEntity.java — 유사 패턴
```

#### Spring Data JPA 인터페이스 (`adapter/out/persistence/repository/`)

```java
// LiveBroadcastJpaRepository.java
public interface LiveBroadcastJpaRepository
        extends JpaRepository<LiveBroadcastJpaEntity, UUID> {
    Optional<LiveBroadcastJpaEntity> findByLiveBroadcastIdAndDeletedStatusFalse(UUID id);
    boolean existsByLiveBroadcastIdAndDeletedStatusFalse(UUID id);
    List<LiveBroadcastJpaEntity> findAllByDeletedStatusFalseAndBroadcastStatusIn(List<BroadcastStatus> statuses);

    @Query("SELECT b.hostId FROM LiveBroadcastJpaEntity b WHERE b.liveBroadcastId = :id")
    UUID findHostIdByBroadcastId(@Param("id") UUID id);
}

// BroadcastProductJpaRepository.java — 유사 패턴
// BroadcastSubscriptionJpaRepository.java — 유사 패턴
```

#### Persistence Adapter (`adapter/out/persistence/adapter/`)

```java
// LiveBroadcastPersistenceAdapter.java
@Component @RequiredArgsConstructor
public class LiveBroadcastPersistenceAdapter implements LiveBroadcastRepositoryPort {
    private final LiveBroadcastJpaRepository jpaRepository;

    @Override
    public LiveBroadcast save(LiveBroadcast broadcast) {
        LiveBroadcastJpaEntity entity = LiveBroadcastJpaEntity.fromDomain(broadcast);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<LiveBroadcast> findById(UUID broadcastId) {
        return jpaRepository.findByLiveBroadcastIdAndDeletedStatusFalse(broadcastId)
            .map(LiveBroadcastJpaEntity::toDomain);
    }

    @Override
    public void softDelete(UUID broadcastId, UUID deletedById) {
        LiveBroadcastJpaEntity entity = jpaRepository
            .findByLiveBroadcastIdAndDeletedStatusFalse(broadcastId)
            .orElseThrow(() -> LiveBroadcastException.notFound(broadcastId));
        entity.delete(deletedById);
        jpaRepository.save(entity);
    }
    // ... 나머지 메서드
}
```

---

### 5.2 Query Adapter (QueryDSL)

**위치**: `adapter/out/persistence/query/`

```java
// BroadcastQueryAdapter.java
@Component @RequiredArgsConstructor
public class BroadcastQueryAdapter implements BroadcastQueryPort {
    private final JPAQueryFactory queryFactory;

    @Override
    public Page<LiveBroadcastResult> searchByName(String keyword, Pageable pageable) {
        QLiveBroadcastJpaEntity b = QLiveBroadcastJpaEntity.liveBroadcastJpaEntity;
        BooleanBuilder condition = new BooleanBuilder();
        condition.and(b.deletedStatus.isFalse());
        if (keyword != null && !keyword.isBlank()) {
            condition.and(b.broadcastName.containsIgnoreCase(keyword));
        }
        List<LiveBroadcastJpaEntity> results = queryFactory
            .selectFrom(b).where(condition)
            .orderBy(b.createdAt.desc())
            .offset(pageable.getOffset()).limit(pageable.getPageSize())
            .fetch();
        long total = queryFactory.selectFrom(b).where(condition).fetchCount();
        List<LiveBroadcastResult> content = results.stream()
            .map(e -> LiveBroadcastResult.from(e.toDomain())).toList();
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<UUID> findProductIdsByBroadcastId(UUID broadcastId, Pageable pageable) {
        QBroadcastProductJpaEntity bp = QBroadcastProductJpaEntity.broadcastProductJpaEntity;
        // ... QueryDSL 구현
    }
}
```

---

### 5.3 Feign Adapter (External Service)

**위치**: `adapter/out/client/`

```java
// ProductFeignAdapter.java
@Component @RequiredArgsConstructor
public class ProductFeignAdapter implements ExternalProductPort {
    private final ProductClient productClient;  // 기존 Feign 클라이언트 재사용

    @Override
    public ExternalProductInfo getProduct(UUID productId) {
        ExternalProductResponseDto dto = productClient.getProduct(productId).getData();
        return new ExternalProductInfo(dto.productId(), dto.productName(), dto.companyId(), dto.price());
    }

    @Override
    public List<ExternalProductInfo> getProducts(List<UUID> productIds) {
        return productClient.getProducts(productIds).getData().stream()
            .map(dto -> new ExternalProductInfo(dto.productId(), dto.productName(), dto.companyId(), dto.price()))
            .toList();
    }
}

// CompanyFeignAdapter.java
@Component @RequiredArgsConstructor
public class CompanyFeignAdapter implements ExternalCompanyPort {
    private final CompanyClient companyClient;

    @Override
    public ExternalCompanyInfo getCompany(UUID companyId) {
        ExternalCompanyResponseDto dto = companyClient.getCompany(companyId).getData();
        return new ExternalCompanyInfo(dto.companyId(), dto.companyName(), dto.isActive());
    }

    @Override
    public boolean isActiveCompany(UUID companyId) {
        return getCompany(companyId).active();
    }
}

// NotificationFeignAdapter.java
@Component @RequiredArgsConstructor
public class NotificationFeignAdapter implements BroadcastAlarmPort {
    private final NotificationClient notificationClient;

    @Override
    public void registerAlarm(UUID broadcastId, UUID userId, LocalDateTime notifyAt) {
        notificationClient.registerBroadcastAlarm(
            new BroadcastAlarmRegisterRequest(broadcastId, userId, notifyAt));
    }

    @Override
    public void deleteAlarm(UUID broadcastId) {
        notificationClient.unregisterBroadcastAlarm(broadcastId);
    }
}
```

---

## 6. Controller 전환 설계

**기존**: `Controller → Service 직접 주입`
**변경**: `Controller → UseCase 인터페이스 주입`

```java
// LiveBroadcastController.java (변경 후)
@RestController @RequestMapping("/api/v1/livebroadcasts")
@RequiredArgsConstructor
public class LiveBroadcastController {
    // UseCase 인터페이스만 주입
    private final CreateBroadcastUseCase createBroadcastUseCase;
    private final UpdateBroadcastUseCase updateBroadcastUseCase;
    private final DeleteBroadcastUseCase deleteBroadcastUseCase;
    private final GetBroadcastUseCase getBroadcastUseCase;
    private final SearchBroadcastUseCase searchBroadcastUseCase;
    private final GetBroadcastSubscribersUseCase getSubscribersUseCase;

    @PostMapping
    @PreAuthorize("hasAnyRole('MASTER', 'SHOW_HOST')")
    public ResponseEntity<ApiResponse<LiveBroadcastResponseDto>> createBroadcast(
            @RequestBody @Valid LiveBroadcastCreateRequestDto requestDto,
            @AuthenticationPrincipal RequestUserDetails user) {
        CreateBroadcastCommand command = new CreateBroadcastCommand(
            requestDto.broadcastName(), requestDto.startTime(), requestDto.endTime());
        LiveBroadcastResult result = createBroadcastUseCase.createBroadcast(
            command, user.getUserId(), requestDto.companyId());
        return ResponseUtil.success(LiveBroadcastResponseDto.from(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LiveBroadcastResponseDto>> getBroadcast(@PathVariable UUID id) {
        LiveBroadcastResult result = getBroadcastUseCase.getBroadcast(id);
        return ResponseUtil.success(LiveBroadcastResponseDto.from(result));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('MASTER', 'SHOW_HOST')")
    public ResponseEntity<ApiResponse<LiveBroadcastResponseDto>> updateBroadcast(
            @PathVariable UUID id,
            @RequestBody LiveBroadcastUpdateRequestDto requestDto,
            @AuthenticationPrincipal RequestUserDetails user) {
        UpdateBroadcastCommand command = new UpdateBroadcastCommand(
            requestDto.broadcastName(), requestDto.startTime(),
            requestDto.endTime(), requestDto.broadcastStatus());
        LiveBroadcastResult result = updateBroadcastUseCase.updateBroadcast(
            id, command, user.getUserId(), user.getRole());
        return ResponseUtil.success(LiveBroadcastResponseDto.from(result));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MASTER', 'SHOW_HOST')")
    public ResponseEntity<ApiResponse<String>> deleteBroadcast(
            @PathVariable UUID id, @AuthenticationPrincipal RequestUserDetails user) {
        deleteBroadcastUseCase.deleteBroadcast(id, user.getUserId(), user.getRole());
        return ResponseUtil.success("방송이 삭제되었습니다.");
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<LiveBroadcastResponseDto>>> search(
            @RequestParam(required = false) String keyword, Pageable pageable) {
        Page<LiveBroadcastResult> results = searchBroadcastUseCase.searchBroadcast(keyword, pageable);
        return ResponseUtil.success(results.map(LiveBroadcastResponseDto::from));
    }

    @GetMapping("/{id}/subscribers")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<ApiResponse<Page<UUID>>> getSubscribers(
            @PathVariable UUID id, Pageable pageable) {
        return ResponseUtil.success(getSubscribersUseCase.getSubscribers(id, pageable));
    }
}
```

BroadcastProductController, BroadcastSubscriptionController도 동일 패턴 적용.

---

## 7. 기존 코드 레거시 처리

### @Deprecated 처리 대상

```java
@Deprecated(since = "hexagonal-ddd-livebroadcast", forRemoval = true)
public class LiveBroadcastService { ... }

@Deprecated(since = "hexagonal-ddd-livebroadcast", forRemoval = true)
public class BroadcastProductService { ... }

@Deprecated(since = "hexagonal-ddd-livebroadcast", forRemoval = true)
public class BroadcastSubscriptionService { ... }

// Validator 클래스들 — 서비스 로직으로 흡수
@Deprecated(since = "hexagonal-ddd-livebroadcast", forRemoval = true)
public class CompanyValidator { ... }

@Deprecated(since = "hexagonal-ddd-livebroadcast", forRemoval = true)
public class ProductValidator { ... }

@Deprecated(since = "hexagonal-ddd-livebroadcast", forRemoval = true)
public class NotificationValidator { ... }

@Deprecated(since = "hexagonal-ddd-livebroadcast", forRemoval = true)
public class LiveBroadcastValidator { ... }

@Deprecated(since = "hexagonal-ddd-livebroadcast", forRemoval = true)
public class PermissionValidator { ... }

@Deprecated(since = "hexagonal-ddd-livebroadcast", forRemoval = true)
public class SubscriptionValidator { ... }

@Deprecated(since = "hexagonal-ddd-livebroadcast", forRemoval = true)
public class BroadcastProductValidator { ... }
```

### 기존 Repository 인터페이스 처리

기존 `domain/repository/` 인터페이스들은 JPA에 직접 의존하지 않으므로 **삭제 대상** (새 Port 인터페이스로 대체):
- `domain/repository/LiveBroadcastRepository.java` → 삭제
- `domain/repository/BroadcastProductRepository.java` → 삭제
- `domain/repository/BroadcastSubscriptionRepository.java` → 삭제
- `domain/repository/query/LiveBroadcastQueryRepository.java` → 삭제
- `domain/repository/query/LiveBroadcastQueryRepositoryImpl.java` → 삭제
- `domain/repository/query/BroadcastProductQueryRepository.java` → 삭제
- `domain/repository/query/BroadcastProductQueryRepositoryImpl.java` → 삭제

---

## 8. 구현 순서 체크리스트

### Step 1: 도메인 모델 분리
- [ ] `domain/model/BaseEntity.java` — JPA 어노테이션 제거 (순수 Java)
- [ ] `domain/model/LiveBroadcast.java` — @Entity 제거, `reconstitute()` 추가, `update()` DTO 의존 제거
- [ ] `domain/model/BroadcastProduct.java` — @Entity 제거, `reconstitute()` 추가
- [ ] `domain/model/BroadcastSubscription.java` — @Entity 제거, `reconstitute()` 추가

### Step 2: Outbound Port 인터페이스 생성
- [ ] `domain/port/out/LiveBroadcastRepositoryPort.java`
- [ ] `domain/port/out/BroadcastProductRepositoryPort.java`
- [ ] `domain/port/out/BroadcastSubscriptionRepositoryPort.java`
- [ ] `domain/port/out/BroadcastQueryPort.java`
- [ ] `domain/port/out/ExternalProductPort.java`
- [ ] `domain/port/out/ExternalCompanyPort.java`
- [ ] `domain/port/out/BroadcastAlarmPort.java`

### Step 3: Inbound UseCase 인터페이스 생성
- [ ] `domain/port/in/CreateBroadcastUseCase.java`
- [ ] `domain/port/in/UpdateBroadcastUseCase.java`
- [ ] `domain/port/in/DeleteBroadcastUseCase.java`
- [ ] `domain/port/in/GetBroadcastUseCase.java`
- [ ] `domain/port/in/SearchBroadcastUseCase.java`
- [ ] `domain/port/in/ConnectBroadcastProductUseCase.java`
- [ ] `domain/port/in/DisconnectBroadcastProductUseCase.java`
- [ ] `domain/port/in/GetBroadcastProductsUseCase.java`
- [ ] `domain/port/in/CheckBroadcastProductExistsUseCase.java`
- [ ] `domain/port/in/SubscribeBroadcastUseCase.java`
- [ ] `domain/port/in/UnsubscribeBroadcastUseCase.java`
- [ ] `domain/port/in/GetMySubscriptionsUseCase.java`
- [ ] `domain/port/in/GetBroadcastSubscribersUseCase.java`
- [ ] `domain/port/in/RegisterBroadcastAlarmUseCase.java`

### Step 4: Result / Command DTO 생성
- [ ] `application/dto/result/LiveBroadcastResult.java`
- [ ] `application/dto/result/BroadcastProductResult.java`
- [ ] `application/dto/result/BroadcastSubscriptionResult.java`
- [ ] `application/dto/result/ExternalProductInfo.java`
- [ ] `application/dto/result/ExternalCompanyInfo.java`
- [ ] `application/dto/command/CreateBroadcastCommand.java`
- [ ] `application/dto/command/UpdateBroadcastCommand.java`

### Step 5: JpaEntity + JpaRepository 생성
- [ ] `adapter/out/persistence/entity/BaseJpaEntity.java`
- [ ] `adapter/out/persistence/entity/LiveBroadcastJpaEntity.java` (fromDomain, toDomain 포함)
- [ ] `adapter/out/persistence/entity/BroadcastProductJpaEntity.java`
- [ ] `adapter/out/persistence/entity/BroadcastSubscriptionJpaEntity.java`
- [ ] `adapter/out/persistence/repository/LiveBroadcastJpaRepository.java`
- [ ] `adapter/out/persistence/repository/BroadcastProductJpaRepository.java`
- [ ] `adapter/out/persistence/repository/BroadcastSubscriptionJpaRepository.java`

### Step 6: PersistenceAdapter 구현
- [ ] `adapter/out/persistence/adapter/LiveBroadcastPersistenceAdapter.java`
- [ ] `adapter/out/persistence/adapter/BroadcastProductPersistenceAdapter.java`
- [ ] `adapter/out/persistence/adapter/BroadcastSubscriptionPersistenceAdapter.java`

### Step 7: QueryDSL Adapter 구현
- [ ] `adapter/out/persistence/query/BroadcastQueryAdapter.java` (implements BroadcastQueryPort)

### Step 8: Feign Adapter 구현
- [ ] `adapter/out/client/ProductFeignAdapter.java` (implements ExternalProductPort)
- [ ] `adapter/out/client/CompanyFeignAdapter.java` (implements ExternalCompanyPort)
- [ ] `adapter/out/client/NotificationFeignAdapter.java` (implements BroadcastAlarmPort)

### Step 9: Application Service 구현
- [ ] `application/service/CreateBroadcastService.java`
- [ ] `application/service/UpdateBroadcastService.java`
- [ ] `application/service/DeleteBroadcastService.java`
- [ ] `application/service/GetBroadcastService.java`
- [ ] `application/service/SearchBroadcastService.java`
- [ ] `application/service/ConnectBroadcastProductService.java`
- [ ] `application/service/DisconnectBroadcastProductService.java`
- [ ] `application/service/GetBroadcastProductsService.java`
- [ ] `application/service/CheckBroadcastProductExistsService.java`
- [ ] `application/service/SubscribeBroadcastService.java`
- [ ] `application/service/UnsubscribeBroadcastService.java`
- [ ] `application/service/GetMySubscriptionsService.java`
- [ ] `application/service/GetBroadcastSubscribersService.java`
- [ ] `application/service/RegisterBroadcastAlarmService.java`

### Step 10: Controller 전환
- [ ] `presentation/controller/LiveBroadcastController.java` — UseCase 주입으로 전환
- [ ] `presentation/controller/BroadcastProductController.java` — UseCase 주입으로 전환
- [ ] `presentation/controller/BroadcastSubscriptionController.java` — UseCase 주입으로 전환

### Step 11: 레거시 처리
- [ ] `LiveBroadcastService.java` — `@Deprecated` 처리
- [ ] `BroadcastProductService.java` — `@Deprecated` 처리
- [ ] `BroadcastSubscriptionService.java` — `@Deprecated` 처리
- [ ] Validator 클래스 6개 — `@Deprecated` 처리
- [ ] 기존 `domain/repository/` 인터페이스 7개 삭제

### Step 12: 빌드 및 테스트 검증
- [ ] `./gradlew :livebroadcast:compileJava` — BUILD SUCCESSFUL
- [ ] `./gradlew :livebroadcast:test` — BUILD SUCCESSFUL

---

## 9. 완료 기준 요약

| 항목 | 기준 |
|------|------|
| 도메인 모델 순수성 | `domain/model/` 내 `import jakarta.persistence.*` 없음 |
| Inbound Port | 14개 UseCase 인터페이스 `domain/port/in/` 위치 |
| Outbound Port | 7개 Port 인터페이스 `domain/port/out/` 위치 |
| Application Service 순수성 | Port 인터페이스만 주입, Feign/JPA 직접 의존 없음 |
| Adapter | 3개 Persistence + 1개 Query + 3개 Feign Adapter |
| 레거시 | 기존 3개 서비스 + 6개 Validator `@Deprecated` |
| 빌드 | `compileJava` 및 `test` BUILD SUCCESSFUL |
| Gap Analysis | Match Rate ≥ 90% |
