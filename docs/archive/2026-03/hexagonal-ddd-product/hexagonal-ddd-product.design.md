# Design: hexagonal-ddd-product

## 메타데이터

| 항목 | 내용 |
|------|------|
| Feature | hexagonal-ddd-product |
| 작성일 | 2026-03-01 |
| 참조 Plan | [hexagonal-ddd-product.plan.md](../../01-plan/features/hexagonal-ddd-product.plan.md) |
| 참조 서비스 | `product/` (port: 19070) |
| 복잡도 | 높음 (이중 서브도메인 + 분산락 + 캐싱 + QueryDSL) |

---

## 1. 현재 구조 분석

### 1.1 현재 아키텍처 문제점

| 문제 | 파일 | 영향 |
|------|------|------|
| 도메인 모델에 JPA 어노테이션 직접 적용 | `Product.java`, `Inventory.java`, `ProductDiscount.java` | 도메인 계층이 JPA에 종속 |
| ProductQueryRepositoryImpl이 domain 패키지에 위치 | `domain/repository/ProductQueryRepositoryImpl.java` | QueryDSL(infrastructure) 코드가 domain에 존재 |
| Application Service에서 Redis 직접 사용 | `ProductService(@Cacheable)`, `InventoryService(StringRedisTemplate)` | Application이 infrastructure에 종속 |
| Application Service에서 Validator 직접 주입 | `ProductService.productValidator`, `companyValidator` | 유효성 검증이 서비스에 분산 |
| Controller에서 Validator 직접 주입 | `ProductController.productValidator` | Presentation이 business logic에 의존 |
| ProductDiscount에 @ManyToOne Product 객체 참조 | `ProductDiscount.product` | 도메인 모델 간 JPA 관계 의존 |

### 1.2 현재 파일 구조 (핵심)

```
product/
├── product/
│   ├── application/service/
│   │   ├── ProductService.java           ← 통합 서비스 (9개 메서드)
│   │   ├── ProductDiscountService.java   ← 할인 서비스
│   │   ├── ProductDiscountCacheService.java ← Redis 직접 사용
│   │   └── ProductRankingService.java    ← Redis 직접 사용
│   ├── domain/
│   │   ├── model/                        ← JPA @Entity 포함
│   │   └── repository/
│   │       ├── ProductRepository.java    ← 순수 인터페이스
│   │       ├── ProductDiscountRepository.java
│   │       ├── ProductQueryRepository.java
│   │       └── ProductQueryRepositoryImpl.java  ← QueryDSL, domain에 위치 (문제)
│   └── infrastructure/repository/
│       ├── JpaProductRepository.java     ← implements ProductRepository
│       └── JpaProductDiscountRepository.java
└── inventory/
    ├── application/service/
    │   └── InventoryService.java         ← 통합 서비스 (8개 메서드 + @DistributedLock)
    ├── domain/
    │   ├── model/                        ← JPA @Entity 포함
    │   └── repository/InventoryRepository.java
    └── infrastructure/repository/
        └── JpaInventoryRepository.java   ← @Query JPQL 원자적 update
```

---

## 2. 목표 헥사고날 아키텍처

### 2.1 전체 패키지 구조 (After)

```
product/src/main/java/com/live_commerce/product/
│
├── product/                            ← 상품 서브도메인
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Product.java            ← 순수 Java (JPA 어노테이션 제거)
│   │   │   ├── ProductDiscount.java    ← 순수 Java (productId: UUID로 전환)
│   │   │   ├── ProductCategory.java    ← enum (그대로)
│   │   │   ├── ProductStatus.java      ← enum (그대로)
│   │   │   ├── RestockNotificationRequest.java  ← 유지 (범위 밖)
│   │   │   └── BaseEntity.java         ← 순수 Java (JPA 제거)
│   │   └── port/
│   │       ├── in/                     ← 인바운드 포트 (UseCase)
│   │       │   ├── CreateProductUseCase.java
│   │       │   ├── GetProductUseCase.java
│   │       │   ├── UpdateProductUseCase.java
│   │       │   ├── DeleteProductUseCase.java
│   │       │   ├── SearchProductsUseCase.java
│   │       │   ├── GetProductsByIdsUseCase.java
│   │       │   ├── GetProductPriceUseCase.java
│   │       │   ├── ApplyLiveDiscountUseCase.java
│   │       │   └── GetPopularProductsUseCase.java
│   │       └── out/                    ← 아웃바운드 포트
│   │           ├── ProductRepositoryPort.java
│   │           ├── ProductDiscountRepositoryPort.java
│   │           ├── ProductQueryPort.java
│   │           ├── ExternalCompanyPort.java
│   │           ├── ProductCachePort.java
│   │           └── InventoryQueryPort.java     ← cross-subdomain query
│   ├── application/
│   │   ├── dto/
│   │   │   ├── command/
│   │   │   │   ├── CreateProductCommand.java
│   │   │   │   └── UpdateProductCommand.java
│   │   │   └── result/
│   │   │       ├── ProductResult.java
│   │   │       ├── ProductPageResult.java
│   │   │       ├── ProductSummaryResult.java
│   │   │       └── ProductPriceResult.java
│   │   └── service/
│   │       ├── CreateProductService.java
│   │       ├── GetProductService.java
│   │       ├── UpdateProductService.java
│   │       ├── DeleteProductService.java
│   │       ├── SearchProductsService.java
│   │       ├── GetProductsByIdsService.java
│   │       ├── GetProductPriceService.java
│   │       ├── ApplyLiveDiscountService.java
│   │       └── GetPopularProductsService.java
│   └── adapter/
│       └── out/
│           ├── persistence/
│           │   ├── ProductJpaEntity.java
│           │   ├── ProductDiscountJpaEntity.java
│           │   ├── ProductJpaRepository.java
│           │   ├── ProductDiscountJpaRepository.java
│           │   ├── ProductPersistenceAdapter.java    ← implements ProductRepositoryPort
│           │   └── ProductDiscountPersistenceAdapter.java
│           ├── query/
│           │   └── ProductQueryAdapter.java          ← implements ProductQueryPort (QueryDSL)
│           ├── client/
│           │   └── CompanyFeignAdapter.java          ← implements ExternalCompanyPort
│           └── cache/
│               └── ProductCacheAdapter.java          ← implements ProductCachePort
│
└── inventory/                          ← 재고 서브도메인
    ├── domain/
    │   ├── model/
    │   │   ├── Inventory.java          ← 순수 Java (JPA 어노테이션 제거)
    │   │   ├── InventoryStatus.java    ← enum (그대로)
    │   │   └── BaseEntity.java         ← 순수 Java
    │   └── port/
    │       ├── in/
    │       │   ├── CreateInventoryUseCase.java
    │       │   ├── GetInventoryUseCase.java
    │       │   ├── DecreaseInventoryUseCase.java
    │       │   ├── IncreaseInventoryUseCase.java
    │       │   ├── HandleInventoryDecreaseEventUseCase.java
    │       │   ├── HandleInventoryRollbackEventUseCase.java
    │       │   ├── CheckInventoryQuantityUseCase.java
    │       │   └── CheckOrderableInventoryUseCase.java
    │       └── out/
    │           ├── InventoryRepositoryPort.java
    │           ├── InventoryEventPublisherPort.java
    │           └── InventoryCachePort.java
    ├── application/
    │   ├── dto/
    │   │   ├── command/
    │   │   └── result/
    │   └── service/
    │       ├── CreateInventoryService.java
    │       ├── GetInventoryService.java
    │       ├── DecreaseInventoryService.java
    │       ├── IncreaseInventoryService.java
    │       ├── HandleInventoryDecreaseEventService.java
    │       ├── HandleInventoryRollbackEventService.java
    │       ├── CheckInventoryQuantityService.java
    │       └── CheckOrderableInventoryService.java
    └── adapter/
        └── out/
            ├── persistence/
            │   ├── InventoryJpaEntity.java
            │   ├── InventoryJpaRepository.java      ← @Query JPQL 유지
            │   └── InventoryPersistenceAdapter.java ← implements InventoryRepositoryPort
            ├── messaging/
            │   └── InventoryEventPublisherAdapter.java ← implements InventoryEventPublisherPort (Outbox 위임)
            └── cache/
                └── InventoryCacheAdapter.java       ← implements InventoryCachePort (Redis sold count)
```

---

## 3. 포트 상세 설계

### 3.1 Product 인바운드 포트 (UseCase)

```java
// CreateProductUseCase.java
public interface CreateProductUseCase {
    ProductResult createProduct(CreateProductCommand command);
}

// GetProductUseCase.java
public interface GetProductUseCase {
    ProductResult getProduct(UUID productId);
}

// UpdateProductUseCase.java
public interface UpdateProductUseCase {
    ProductResult updateProduct(UpdateProductCommand command);
}

// DeleteProductUseCase.java
public interface DeleteProductUseCase {
    void deleteProduct(UUID productId, UUID requestUserId, String requestUserRole, UUID requestUserCompanyId);
}

// SearchProductsUseCase.java
public interface SearchProductsUseCase {
    ProductPageResult searchProducts(ProductSearchCondition condition, Pageable pageable);
}

// GetProductsByIdsUseCase.java
public interface GetProductsByIdsUseCase {
    List<ProductSummaryResult> getProductsByIds(List<UUID> productIds);
}

// GetProductPriceUseCase.java
public interface GetProductPriceUseCase {
    ProductPriceResult getProductPrice(UUID productId);
    ProductPriceResult getPriceForOrder(UUID productId);
}

// ApplyLiveDiscountUseCase.java
public interface ApplyLiveDiscountUseCase {
    void applyLiveDiscount(UUID productId, int discountPrice, Duration duration, UUID appliedById);
}

// GetPopularProductsUseCase.java
public interface GetPopularProductsUseCase {
    List<PopularProductResult> getPopularProducts();
}
```

### 3.2 Product 아웃바운드 포트

```java
// ProductRepositoryPort.java
public interface ProductRepositoryPort {
    Product save(Product product);
    Optional<Product> findById(UUID productId);
    boolean existsById(UUID productId);
    List<Product> findAllByIds(List<UUID> productIds);
}

// ProductDiscountRepositoryPort.java
public interface ProductDiscountRepositoryPort {
    ProductDiscount save(ProductDiscount productDiscount);
}

// ProductQueryPort.java
public interface ProductQueryPort {
    Page<Product> search(ProductSearchCondition condition, Pageable pageable);
}

// ExternalCompanyPort.java
public interface ExternalCompanyPort {
    boolean existsActiveCompany(UUID companyId);
}

// ProductCachePort.java
public interface ProductCachePort {
    Optional<Integer> getDiscountPrice(UUID productId);
    void setDiscountPrice(UUID productId, int discountPrice, Duration duration);
}

// InventoryQueryPort.java  ← cross-subdomain query (product → inventory)
public interface InventoryQueryPort {
    boolean isSoldOut(UUID productId);
}
```

### 3.3 Inventory 인바운드 포트 (UseCase)

```java
// CreateInventoryUseCase.java
public interface CreateInventoryUseCase {
    InventoryResult createInventory(UUID productId, int quantity, int reservedQuantity,
                                    int availableQuantity, InventoryStatus status);
}

// GetInventoryUseCase.java
public interface GetInventoryUseCase {
    InventoryResult getInventory(UUID inventoryId);
}

// DecreaseInventoryUseCase.java  ← HTTP 엔드포인트용 (분산락 적용)
public interface DecreaseInventoryUseCase {
    void decreaseInventory(UUID productId, int quantity);
}

// IncreaseInventoryUseCase.java  ← HTTP 엔드포인트용 (분산락 적용)
public interface IncreaseInventoryUseCase {
    void increaseInventory(UUID productId, int quantity);
}

// HandleInventoryDecreaseEventUseCase.java  ← Kafka consumer용 (분산락 + Outbox)
public interface HandleInventoryDecreaseEventUseCase {
    void handle(UUID orderId, UUID productId, int quantity);
}

// HandleInventoryRollbackEventUseCase.java  ← Kafka consumer용 (분산락)
public interface HandleInventoryRollbackEventUseCase {
    void handle(UUID productId, int quantity);
}

// CheckInventoryQuantityUseCase.java
public interface CheckInventoryQuantityUseCase {
    CheckQuantityResult checkQuantity(UUID productId);
}

// CheckOrderableInventoryUseCase.java
public interface CheckOrderableInventoryUseCase {
    CheckOrderableResult checkOrderable(UUID productId, int orderQuantity);
}
```

### 3.4 Inventory 아웃바운드 포트

```java
// InventoryRepositoryPort.java
public interface InventoryRepositoryPort {
    Inventory save(Inventory inventory);
    Optional<Inventory> findById(UUID inventoryId);
    Optional<Inventory> findByProductId(UUID productId);
    boolean existsOrderable(UUID productId, int quantity);
    int decreaseAtomically(UUID productId, int quantity);     // @Query 위임
    int increaseAtomically(UUID productId, int quantity);     // @Query 위임
}

// InventoryEventPublisherPort.java
public interface InventoryEventPublisherPort {
    void publishDecreased(UUID orderId, UUID productId, int quantity);
    void publishSoldOut(UUID productId);
}

// InventoryCachePort.java
public interface InventoryCachePort {
    void incrementSoldCount(UUID productId, int quantity);    // product:sold_count:{id}
    Map<UUID, Long> getSoldCountMap();                        // 전체 sold count 조회
}
```

---

## 4. 도메인 모델 변경 설계

### 4.1 Product.java (Before → After)

```java
// Before
@Entity @Table(name = "p_product", schema = "products")
public class Product extends BaseEntity { ... }

// After (순수 Java)
public class Product extends BaseEntity {
    private UUID productId;
    private UUID companyId;
    private String name;
    private String description;
    private Integer price;
    private ProductCategory category;
    private ProductStatus productStatus;

    // 기존 메서드 유지: create(), update(command), changeStatus(), delete()
    public void update(UpdateProductCommand command) { ... }
}
```

### 4.2 ProductDiscount.java (Before → After)

**핵심 변경**: `@ManyToOne Product product` → `UUID productId`

```java
// Before
@Entity @Table(name = "p_product_discount", schema = "products")
public class ProductDiscount extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;  // ← JPA 관계 의존
    ...
}

// After (순수 Java)
public class ProductDiscount extends BaseEntity {
    private Long id;
    private UUID productId;   // ← ID 참조로 전환
    private Integer discountPrice;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private UUID appliedBy;

    public boolean isActiveNow() { ... }  // 유지
}
```

### 4.3 ProductDiscountJpaEntity.java (신규)

```java
@Entity @Table(name = "p_product_discount", schema = "products")
public class ProductDiscountJpaEntity extends BaseJpaEntity {
    @Id @GeneratedValue
    private Long id;

    // Option A: productId FK만 유지 (단순화)
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    private Integer discountPrice;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private UUID appliedBy;

    public static ProductDiscountJpaEntity from(ProductDiscount domain) { ... }
    public ProductDiscount toDomain() { ... }
}
```

### 4.4 Inventory.java (Before → After)

```java
// After (순수 Java)
public class Inventory extends BaseEntity {
    private UUID inventoryId;
    private UUID productId;
    private Integer quantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    private InventoryStatus inventoryStatus;

    // 기존 메서드 유지: create(), decrease(), increase(), discontinue(), changeStatus()
}
```

### 4.5 BaseEntity (순수 Java로 전환)

```java
// Before
@MappedSuperclass @EntityListeners(AuditingEntityListener.class)
public class BaseEntity {
    @CreatedDate LocalDateTime createdAt;
    @LastModifiedDate LocalDateTime updatedAt;
    boolean deletedStatus;
    UUID deletedBy;
}

// After (순수 Java)
public class BaseEntity {
    protected LocalDateTime createdAt;
    protected LocalDateTime updatedAt;
    protected boolean deletedStatus;
    protected UUID deletedBy;

    public void delete(UUID deletedBy) {
        this.deletedStatus = true;
        this.deletedBy = deletedBy;
    }
}
```

---

## 5. 어댑터 상세 설계

### 5.1 ProductJpaEntity.java

```java
@Entity @Table(name = "p_product", schema = "products")
@MappedSuperclass  // BaseJpaEntity 사용
public class ProductJpaEntity extends BaseJpaEntity {
    @Id @UuidGenerator
    private UUID productId;
    private UUID companyId;
    private String name;
    private String description;
    private Integer price;
    @Enumerated(EnumType.STRING) private ProductCategory category;
    @Enumerated(EnumType.STRING) private ProductStatus productStatus;

    public static ProductJpaEntity from(Product domain) { ... }
    public Product toDomain() { ... }
}
```

### 5.2 ProductPersistenceAdapter.java

```java
@Component @RequiredArgsConstructor
public class ProductPersistenceAdapter implements ProductRepositoryPort {
    private final ProductJpaRepository productJpaRepository;

    @Override
    public Product save(Product product) {
        return productJpaRepository.save(ProductJpaEntity.from(product)).toDomain();
    }

    @Override
    public Optional<Product> findById(UUID productId) {
        return productJpaRepository.findByProductIdAndDeletedStatusFalse(productId)
                .map(ProductJpaEntity::toDomain);
    }

    @Override
    public boolean existsById(UUID productId) {
        return productJpaRepository.existsByProductIdAndDeletedStatusFalse(productId);
    }

    @Override
    public List<Product> findAllByIds(List<UUID> productIds) {
        return productJpaRepository.findAllByProductIdInAndDeletedStatusFalse(productIds)
                .stream().map(ProductJpaEntity::toDomain).toList();
    }
}
```

### 5.3 ProductQueryAdapter.java (QueryDSL 이동)

기존 `ProductQueryRepositoryImpl` 로직을 **adapter/out/query**로 이동:

```java
@Component @RequiredArgsConstructor
public class ProductQueryAdapter implements ProductQueryPort {
    private final JPAQueryFactory queryFactory;
    private final QProductJpaEntity product = QProductJpaEntity.productJpaEntity;

    @Override
    public Page<Product> search(ProductSearchCondition condition, Pageable pageable) {
        // 기존 ProductQueryRepositoryImpl 로직 이동 (QProduct → QProductJpaEntity)
        List<ProductJpaEntity> content = queryFactory.selectFrom(product)
                .where(containsKeyword(condition.keyword()))
                .orderBy(getSort(condition))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory.select(product.count()).from(product)
                .where(containsKeyword(condition.keyword()))
                .fetchOne();

        return new PageImpl<>(
                content.stream().map(ProductJpaEntity::toDomain).toList(),
                pageable, total != null ? total : 0L);
    }
}
```

**주의**: QClass가 `QProduct` → `QProductJpaEntity`로 변경됨. `annotationProcessor` 자동 생성.

### 5.4 CompanyFeignAdapter.java

```java
@Component @RequiredArgsConstructor
public class CompanyFeignAdapter implements ExternalCompanyPort {
    private final CompanyClient companyClient;   // 기존 FeignClient 재사용

    @Override
    public boolean existsActiveCompany(UUID companyId) {
        try {
            ApiResponse<ExternalCompanyResponseDto> response = companyClient.getCompany(companyId);
            return response != null && response.data() != null;
        } catch (Exception e) {
            return false;
        }
    }
}
```

### 5.5 ProductCacheAdapter.java

```java
@Component @RequiredArgsConstructor
public class ProductCacheAdapter implements ProductCachePort {
    private final RedisTemplate<String, String> redisTemplate;
    private static final String DISCOUNT_KEY_PREFIX = "discount:";

    @Override
    public Optional<Integer> getDiscountPrice(UUID productId) {
        String value = redisTemplate.opsForValue().get(DISCOUNT_KEY_PREFIX + productId);
        return Optional.ofNullable(value).map(Integer::valueOf);
    }

    @Override
    public void setDiscountPrice(UUID productId, int discountPrice, Duration duration) {
        redisTemplate.opsForValue().set(DISCOUNT_KEY_PREFIX + productId,
                String.valueOf(discountPrice), duration);
    }
}
```

### 5.6 InventoryJpaEntity.java + InventoryPersistenceAdapter.java

```java
@Entity @Table(name = "p_inventory", schema = "inventories",
    uniqueConstraints = @UniqueConstraint(columnNames = "product_id"))
public class InventoryJpaEntity extends BaseJpaEntity {
    @Id @UuidGenerator private UUID inventoryId;
    @Column(name = "product_id", nullable = false) private UUID productId;
    private Integer quantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    @Enumerated(EnumType.STRING) private InventoryStatus inventoryStatus;

    public static InventoryJpaEntity from(Inventory domain) { ... }
    public Inventory toDomain() { ... }
}

// InventoryJpaRepository: @Query JPQL 메서드 유지
public interface InventoryJpaRepository extends JpaRepository<InventoryJpaEntity, UUID> {
    Optional<InventoryJpaEntity> findByInventoryIdAndDeletedStatusFalse(UUID inventoryId);
    Optional<InventoryJpaEntity> findByProductIdAndDeletedStatusFalse(UUID productId);

    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM InventoryJpaEntity i ...")
    boolean existsOrderableInventory(@Param("productId") UUID productId, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE InventoryJpaEntity i SET ...")
    int decreaseInventoryAtomically(@Param("productId") UUID productId, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE InventoryJpaEntity i SET ...")
    int increaseInventoryAtomically(@Param("productId") UUID productId, @Param("quantity") int quantity);
}
```

### 5.7 InventoryEventPublisherAdapter.java

```java
@Component @RequiredArgsConstructor
public class InventoryEventPublisherAdapter implements InventoryEventPublisherPort {
    private final OutboxEventHelper outboxEventHelper;   // 기존 OutboxEventHelper 재사용

    @Override
    public void publishDecreased(UUID orderId, UUID productId, int quantity) {
        InventoryDecreasedEvent event = InventoryDecreasedEvent.of(orderId, productId, quantity);
        outboxEventHelper.saveEvent("INVENTORY", orderId, "INVENTORY_DECREASED", "inventory-decreased", event);
    }

    @Override
    public void publishSoldOut(UUID productId) {
        InventorySoldOutEvent event = InventorySoldOutEvent.of(productId);
        outboxEventHelper.saveEvent("INVENTORY", productId, "INVENTORY_SOLD_OUT", "inventory-sold-out", event);
    }
}
```

### 5.8 InventoryCacheAdapter.java

```java
@Component @RequiredArgsConstructor
public class InventoryCacheAdapter implements InventoryCachePort {
    private final StringRedisTemplate redisTemplate;
    private static final String SOLD_COUNT_KEY_PREFIX = "product:sold_count:";

    @Override
    public void incrementSoldCount(UUID productId, int quantity) {
        redisTemplate.opsForValue().increment(SOLD_COUNT_KEY_PREFIX + productId, quantity);
    }

    @Override
    public Map<UUID, Long> getSoldCountMap() {
        Set<String> keys = redisTemplate.keys(SOLD_COUNT_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) return Collections.emptyMap();
        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        Map<UUID, Long> result = new HashMap<>();
        int idx = 0;
        for (String key : keys) {
            String value = values.get(idx++);
            if (value != null) {
                UUID productId = UUID.fromString(key.replace(SOLD_COUNT_KEY_PREFIX, ""));
                result.put(productId, Long.parseLong(value));
            }
        }
        return result;
    }
}
```

---

## 6. Application Service 상세 설계

### 6.1 GetProductService.java

```java
@Service @RequiredArgsConstructor
public class GetProductService implements GetProductUseCase {
    private final ProductRepositoryPort productRepositoryPort;
    private final InventoryQueryPort inventoryQueryPort;    // cross-subdomain query port

    @Override
    @Transactional(readOnly = true)
    public ProductResult getProduct(UUID productId) {
        Product product = productRepositoryPort.findById(productId)
                .orElseThrow(ProductException::forProductNotFound);
        boolean soldOut = inventoryQueryPort.isSoldOut(product.getProductId());
        return ProductResult.from(product, soldOut);
    }
}
```

### 6.2 CreateProductService.java

```java
@Service @RequiredArgsConstructor
public class CreateProductService implements CreateProductUseCase {
    private final ProductRepositoryPort productRepositoryPort;
    private final ExternalCompanyPort externalCompanyPort;

    @Override
    @Transactional
    public ProductResult createProduct(CreateProductCommand command) {
        if (!externalCompanyPort.existsActiveCompany(command.companyId())) {
            throw ProductException.forCompanyNotFound();
        }
        validateOwnerOrMaster(command);
        Product product = Product.create(command.companyId(), command.name(),
                command.description(), command.price(), command.category());
        Product saved = productRepositoryPort.save(product);
        return ProductResult.from(saved, false);
    }
}
```

### 6.3 ApplyLiveDiscountService.java

```java
@Service @RequiredArgsConstructor
public class ApplyLiveDiscountService implements ApplyLiveDiscountUseCase {
    private final ProductRepositoryPort productRepositoryPort;
    private final ProductDiscountRepositoryPort productDiscountRepositoryPort;
    private final ProductCachePort productCachePort;

    @Override
    @Transactional
    public void applyLiveDiscount(UUID productId, int discountPrice, Duration duration, UUID appliedById) {
        Product product = productRepositoryPort.findById(productId)
                .orElseThrow(ProductException::forProductNotFound);
        if (discountPrice >= product.getPrice()) {
            throw new IllegalArgumentException("할인 가격은 원래 가격보다 낮아야 합니다.");
        }
        ProductDiscount discount = ProductDiscount.create(productId, discountPrice,
                LocalDateTime.now(), LocalDateTime.now().plus(duration), appliedById);
        productDiscountRepositoryPort.save(discount);
        productCachePort.setDiscountPrice(productId, discountPrice, duration);
    }
}
```

### 6.4 GetPopularProductsService.java

```java
@Service @RequiredArgsConstructor
public class GetPopularProductsService implements GetPopularProductsUseCase {
    private final ProductRepositoryPort productRepositoryPort;
    private final InventoryCachePort inventoryCachePort;    // sold count

    @Override
    public List<PopularProductResult> getPopularProducts() {
        Map<UUID, Long> soldCountMap = inventoryCachePort.getSoldCountMap();
        List<UUID> top10Ids = soldCountMap.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(10).map(Map.Entry::getKey).toList();
        List<Product> products = productRepositoryPort.findAllByIds(top10Ids);
        return products.stream()
                .map(p -> PopularProductResult.from(p, soldCountMap.get(p.getProductId())))
                .toList();
    }
}
```

### 6.5 HandleInventoryDecreaseEventService.java (분산락 + Outbox)

```java
@Service @RequiredArgsConstructor
public class HandleInventoryDecreaseEventService implements HandleInventoryDecreaseEventUseCase {
    private final InventoryRepositoryPort inventoryRepositoryPort;
    private final InventoryEventPublisherPort inventoryEventPublisherPort;
    private final InventoryCachePort inventoryCachePort;

    @Override
    @DistributedLock(key = "#productId")
    @Transactional
    public void handle(UUID orderId, UUID productId, int quantity) {
        int updated = inventoryRepositoryPort.decreaseAtomically(productId, quantity);
        if (updated == 0) {
            throw InventoryException.forInventoryOutOfStock();
        }
        inventoryCachePort.incrementSoldCount(productId, quantity);

        // soldOut 체크 후 이벤트
        Inventory inventory = inventoryRepositoryPort.findByProductId(productId)
                .orElseThrow(InventoryException::forInventoryNotFound);
        if (inventory.getAvailableQuantity() == 0) {
            inventoryEventPublisherPort.publishSoldOut(productId);
        }
        inventoryEventPublisherPort.publishDecreased(orderId, productId, quantity);
    }
}
```

### 6.6 DecreaseInventoryService.java (HTTP 엔드포인트용, 분산락)

```java
@Service @RequiredArgsConstructor
public class DecreaseInventoryService implements DecreaseInventoryUseCase {
    private final InventoryRepositoryPort inventoryRepositoryPort;

    @Override
    @DistributedLock(key = "#productId")
    @Transactional
    public void decreaseInventory(UUID productId, int quantity) {
        int updated = inventoryRepositoryPort.decreaseAtomically(productId, quantity);
        if (updated == 0) {
            throw InventoryException.forInventoryOutOfStock();
        }
    }
}
```

---

## 7. Controller 전환 설계

### 7.1 ProductController (After)

```java
@RestController @RequestMapping("/api/v1/products") @RequiredArgsConstructor
public class ProductController {
    private final CreateProductUseCase createProductUseCase;
    private final GetProductUseCase getProductUseCase;
    private final UpdateProductUseCase updateProductUseCase;
    private final DeleteProductUseCase deleteProductUseCase;
    private final SearchProductsUseCase searchProductsUseCase;
    private final GetProductsByIdsUseCase getProductsByIdsUseCase;
    private final GetProductPriceUseCase getProductPriceUseCase;
    private final ApplyLiveDiscountUseCase applyLiveDiscountUseCase;
    private final GetPopularProductsUseCase getPopularProductsUseCase;

    // 기존 엔드포인트 매핑 유지, 서비스 직접 주입 → UseCase 주입으로 전환
}
```

### 7.2 InventoryController (After)

```java
@RestController @RequestMapping("/api/v1/inventories") @RequiredArgsConstructor
public class InventoryController {
    private final CreateInventoryUseCase createInventoryUseCase;
    private final GetInventoryUseCase getInventoryUseCase;
    private final DecreaseInventoryUseCase decreaseInventoryUseCase;
    private final IncreaseInventoryUseCase increaseInventoryUseCase;
    private final CheckInventoryQuantityUseCase checkInventoryQuantityUseCase;
    private final CheckOrderableInventoryUseCase checkOrderableInventoryUseCase;
}
```

### 7.3 InventoryEventConsumer (After)

```java
@Service @RequiredArgsConstructor
public class InventoryEventConsumer {
    private final HandleInventoryDecreaseEventUseCase handleDecreaseUseCase;
    private final HandleInventoryRollbackEventUseCase handleRollbackUseCase;

    @KafkaListener(topics = "inventory-decrease", ...)
    public void consumeOrderCreated(InventoryDecreaseRequestEvent event) {
        handleDecreaseUseCase.handle(event.orderId(), event.productId(), event.quantity());
    }

    @KafkaListener(topics = "inventory-rollback")
    public void consumeInventoryRollback(InventoryRollbackEvent event) {
        handleRollbackUseCase.handle(event.productId(), event.quantity());
    }
}
```

---

## 8. InventoryQueryPort 어댑터 (cross-subdomain)

product 서브도메인의 `GetProductService`가 sold-out 상태를 조회하기 위한 포트:

```java
// product/product/domain/port/out/InventoryQueryPort.java
public interface InventoryQueryPort {
    boolean isSoldOut(UUID productId);
}

// product/product/adapter/out/persistence/InventoryQueryAdapter.java
@Component @RequiredArgsConstructor
public class InventoryQueryAdapter implements InventoryQueryPort {
    private final InventoryJpaRepository inventoryJpaRepository;   // inventory 어댑터 JPA 재사용

    @Override
    public boolean isSoldOut(UUID productId) {
        return inventoryJpaRepository.findByProductIdAndDeletedStatusFalse(productId)
                .map(e -> e.getQuantity() <= 0)
                .orElse(true);
    }
}
```

---

## 9. 삭제 대상 파일 (레거시 클린업)

### 9.1 도메인 계층 (JPA 의존 제거)

| 파일 | 처리 | 대체 파일 |
|------|------|-----------|
| `product/domain/repository/ProductRepository.java` | **삭제** | `domain/port/out/ProductRepositoryPort.java` |
| `product/domain/repository/ProductDiscountRepository.java` | **삭제** | `domain/port/out/ProductDiscountRepositoryPort.java` |
| `product/domain/repository/ProductQueryRepository.java` | **삭제** | `domain/port/out/ProductQueryPort.java` |
| `product/domain/repository/ProductQueryRepositoryImpl.java` | **삭제** | `adapter/out/query/ProductQueryAdapter.java` |
| `inventory/domain/repository/InventoryRepository.java` | **삭제** | `domain/port/out/InventoryRepositoryPort.java` |

### 9.2 인프라 레포지토리

| 파일 | 처리 | 대체 파일 |
|------|------|-----------|
| `product/infrastructure/repository/JpaProductRepository.java` | **삭제** | `adapter/out/persistence/ProductJpaRepository.java` |
| `product/infrastructure/repository/JpaProductDiscountRepository.java` | **삭제** | `adapter/out/persistence/ProductDiscountJpaRepository.java` |
| `inventory/infrastructure/repository/JpaInventoryRepository.java` | **삭제** | `adapter/out/persistence/InventoryJpaRepository.java` |

### 9.3 Application Service (통합 서비스 분리)

| 파일 | 처리 | 대체 파일 |
|------|------|-----------|
| `product/application/service/ProductService.java` | **삭제** | 9개 UseCase 서비스로 분리 |
| `product/application/service/ProductDiscountService.java` | **삭제** | `ApplyLiveDiscountService.java`에 통합 |
| `product/application/service/ProductDiscountCacheService.java` | **삭제** | `ProductCacheAdapter.java`로 이동 |
| `product/application/service/ProductRankingService.java` | **삭제** | `GetPopularProductsService.java`로 대체 |
| `inventory/application/service/InventoryService.java` | **삭제** | 8개 UseCase 서비스로 분리 |

### 9.4 Mapper / Validator

| 파일 | 처리 | 비고 |
|------|------|------|
| `product/application/mapper/ProductMapper.java` | **삭제** | JpaEntity.from/toDomain + Result.from()으로 대체 |
| `inventory/application/mapper/InventoryMapper.java` | **삭제** | JpaEntity.from/toDomain으로 대체 |
| `product/application/validation/CompanyValidator.java` | **삭제** | CreateProductService에 inline 검증 |
| `product/application/validation/ProductValidator.java` | **삭제** | 각 Service에 inline 검증 |
| `product/application/validation/PermissionValidator.java` | **삭제** | 각 Service에 inline 권한 체크 |
| `inventory/application/validation/InventoryValidator.java` | **삭제** | 각 Service에 inline 검증 |

### 9.5 기타

| 파일 | 처리 | 비고 |
|------|------|------|
| `inventory/infrastructure/redis/InventoryRedisService.java` | **삭제** | 미사용 클래스 (InventoryCacheAdapter로 대체) |
| `inventory/infrastructure/redisson/test/RedissonLockInventoryTestService.java` | **삭제** | 테스트 유틸 |
| `InventoryLockTestController.java` | **삭제** | 테스트 컨트롤러 |
| `product/infrastructure/kafka/InventoryTestProducerController.java` | **삭제** | 테스트 컨트롤러 |

---

## 10. ADR (Architecture Decision Records)

### ADR-1: ProductDiscount @ManyToOne → UUID productId 전환

**결정**: `ProductDiscount.product: Product` → `ProductDiscount.productId: UUID`

**근거**:
- 도메인 모델이 순수 Java여야 함 — JPA 관계 객체 직접 참조 불가
- JpaEntity(`ProductDiscountJpaEntity`)에서 `@Column(name="product_id")` UUID FK로 단순화
- 기존 `@ManyToOne` 제거로 불필요한 N+1 쿼리 위험 제거

**영향**: `ProductDiscountJpaEntity`는 productId만 저장. `ApplyLiveDiscountService`에서 product 조회 후 productId만 전달.

### ADR-2: @DistributedLock Application Service 유지

**결정**: `@DistributedLock` 어노테이션을 Application Service 메서드에 유지

**근거**:
- Port 인터페이스에 AOP 어노테이션 적용 불가
- `payment/application/service/ReadyPaymentService` 선례와 동일
- Adapter에 Lock을 두면 원자성 보장이 어려움 (Outbox와 Lock 범위 불일치)
- 분산락은 application-level cross-cutting concern으로 허용

**영향**: `HandleInventoryDecreaseEventService`, `DecreaseInventoryService`, `IncreaseInventoryService`에 `@DistributedLock` 유지

### ADR-3: @Cacheable 제거 → ProductCachePort 명시적 호출

**결정**: `@Cacheable(value="productDetail")` 제거, `ProductCachePort.getDiscountPrice()/setDiscountPrice()`로 전환

**근거**:
- `@Cacheable`은 Spring AOP infrastructure concern — Application Service가 직접 의존하지 않아야 함
- `ProductCachePort`를 통한 명시적 호출로 투명성 향상
- 기존 할인가 캐시(discount: prefix)만 포트로 추상화

**범위**: `productDetail` 캐시(상품 상세 전체 캐시)는 복잡도 대비 효과 미미 → 제거. 할인가 캐시(`discount:`)만 유지.

### ADR-4: ProductQueryRepositoryImpl → adapter/out/query로 이동

**결정**: QueryDSL 구현체를 `domain/repository`에서 `adapter/out/query`로 이동

**근거**:
- QueryDSL은 JPA 기반 infrastructure 기술 — domain 계층에 위치 부적절
- `QProduct` → `QProductJpaEntity`로 Q-class 대상 변경 필요
- `ProductQueryPort` 인터페이스를 domain에 두고, 구현만 adapter로 이동

### ADR-5: OutboxEventHelper 위치 유지

**결정**: `product.product.infrastructure.outbox.OutboxEventHelper` 위치 그대로 유지

**근거**:
- 이미 동작 중인 Outbox 인프라 — 마이그레이션 불필요
- `InventoryEventPublisherAdapter`가 이 Helper를 포트 구현에서 위임하는 방식으로 사용
- inventory 서브도메인에서 `product.product.infrastructure.outbox` 패키지 참조 허용 (동일 서비스 내)

### ADR-6: InventoryCachePort.getSoldCountMap() 설계

**결정**: `GetPopularProductsService`가 `InventoryCachePort.getSoldCountMap()` 전체 맵을 가져옴

**근거**:
- `ProductRankingService`의 기존 로직 — Redis keys 패턴 매칭 후 multiGet
- 상위 10개 정렬은 Application Service에서 담당 (포트는 데이터만 제공)
- 대용량 케이스 고려 시 향후 별도 Sorted Set 도입 가능 (현행 유지)

### ADR-7: RestockNotificationRequest 마이그레이션 제외

**결정**: `RestockNotificationRequest` 도메인 모델은 현행 유지 (범위 밖)

**근거**: 현재 서비스 코드에서 사용 흔적 없음 — 불필요한 마이그레이션 위험 회피

---

## 11. 구현 체크리스트 (Gap Analysis 기준)

### Section 1: Product 도메인 모델 JPA 분리

| # | 체크리스트 | 검증 방법 |
|---|-----------|----------|
| P-1 | `Product.java` — `@Entity`, `@Table`, `@Id`, `@UuidGenerator`, `@Enumerated`, `import jakarta.persistence.*` 제거 | 파일에 `jakarta.persistence` import 없음 |
| P-2 | `ProductDiscount.java` — JPA 어노테이션 제거 + `product: Product` → `productId: UUID` | 파일에 `@ManyToOne`, `@JoinColumn` 없음; `productId: UUID` 필드 존재 |
| P-3 | `Inventory.java` — `@Entity`, `@Table`, `@Id`, JPA 어노테이션 제거 | 파일에 `jakarta.persistence` import 없음 |
| P-4 | `BaseEntity.java` (product, inventory 양쪽) — `@MappedSuperclass`, `@EntityListeners` 제거 | JPA import 없음 |

### Section 2: Product Port 생성

| # | 체크리스트 | 검증 방법 |
|---|-----------|----------|
| P-5 | `product/domain/port/in/` 에 9개 UseCase 인터페이스 생성 | 파일 9개 존재 |
| P-6 | `product/domain/port/out/` 에 6개 Port 인터페이스 생성 | ProductRepositoryPort, ProductDiscountRepositoryPort, ProductQueryPort, ExternalCompanyPort, ProductCachePort, InventoryQueryPort |

### Section 3: Product Adapter 생성

| # | 체크리스트 | 검증 방법 |
|---|-----------|----------|
| P-7 | `ProductJpaEntity.java` 생성 — `@Entity`, `from()`, `toDomain()` | 파일 존재, from/toDomain 메서드 |
| P-8 | `ProductDiscountJpaEntity.java` 생성 — `productId: UUID` FK (not @ManyToOne) | `@ManyToOne` 없음, `productId: UUID` 필드 |
| P-9 | `ProductJpaRepository.java` 생성 (adapter/out/persistence) | `extends JpaRepository<ProductJpaEntity, UUID>` |
| P-10 | `ProductPersistenceAdapter.java` — `implements ProductRepositoryPort` | save/findById/existsById/findAllByIds 구현 |
| P-11 | `ProductQueryAdapter.java` — `implements ProductQueryPort`, QueryDSL `QProductJpaEntity` 사용 | adapter/out/query에 위치 |
| P-12 | `CompanyFeignAdapter.java` — `implements ExternalCompanyPort` | existsActiveCompany() 메서드 |
| P-13 | `ProductCacheAdapter.java` — `implements ProductCachePort` | getDiscountPrice/setDiscountPrice |
| P-14 | `InventoryQueryAdapter.java` — `implements InventoryQueryPort` | isSoldOut() 구현 |

### Section 4: Product Application Service 생성

| # | 체크리스트 | 검증 방법 |
|---|-----------|----------|
| P-15 | 9개 Product Application Service 생성 | 9개 파일 존재, Port만 주입 (`@Repository`, `@Entity` import 없음) |
| P-16 | `GetProductService` — `ProductRepositoryPort`, `InventoryQueryPort` 주입 | 해당 필드 존재 |
| P-17 | `ApplyLiveDiscountService` — `ProductDiscountRepositoryPort`, `ProductCachePort` 주입 | 해당 필드 존재 |
| P-18 | `GetPopularProductsService` — `ProductRepositoryPort`, `InventoryCachePort` 주입 | 해당 필드 존재 |

### Section 5: Inventory Port 생성

| # | 체크리스트 | 검증 방법 |
|---|-----------|----------|
| I-1 | `inventory/domain/port/in/` 에 8개 UseCase 인터페이스 생성 | 파일 8개 존재 |
| I-2 | `inventory/domain/port/out/` 에 3개 Port 인터페이스 생성 | InventoryRepositoryPort, InventoryEventPublisherPort, InventoryCachePort |

### Section 6: Inventory Adapter 생성

| # | 체크리스트 | 검증 방법 |
|---|-----------|----------|
| I-3 | `InventoryJpaEntity.java` 생성 — from/toDomain 포함 | 파일 존재 |
| I-4 | `InventoryJpaRepository.java` 생성 — `@Query` JPQL 3개 유지 | decreaseInventoryAtomically, increaseInventoryAtomically, existsOrderableInventory |
| I-5 | `InventoryPersistenceAdapter.java` — `implements InventoryRepositoryPort` | 6개 메서드 구현 |
| I-6 | `InventoryEventPublisherAdapter.java` — `implements InventoryEventPublisherPort`, OutboxEventHelper 위임 | publishDecreased/publishSoldOut |
| I-7 | `InventoryCacheAdapter.java` — `implements InventoryCachePort` | incrementSoldCount/getSoldCountMap |

### Section 7: Inventory Application Service 생성

| # | 체크리스트 | 검증 방법 |
|---|-----------|----------|
| I-8 | 8개 Inventory Application Service 생성 | Port만 주입 |
| I-9 | `HandleInventoryDecreaseEventService` — `@DistributedLock` + `@Transactional`, InventoryEventPublisherPort 사용 | 어노테이션 존재, publishDecreased/publishSoldOut 호출 |
| I-10 | `DecreaseInventoryService` — `@DistributedLock`, `increaseInventoryUseCase` 독립 | decreaseAtomically 호출 |

### Section 8: Controller/Consumer 전환

| # | 체크리스트 | 검증 방법 |
|---|-----------|----------|
| C-1 | `ProductController` — Service 주입 → UseCase 주입 | `ProductService` 필드 없음 |
| C-2 | `InventoryController` — Service 주입 → UseCase 주입 | `InventoryService` 필드 없음 |
| C-3 | `InventoryEventConsumer` — `InventoryService` → `HandleInventoryDecreaseEventUseCase`, `HandleInventoryRollbackEventUseCase` | `InventoryService` 필드 없음 |

### Section 9: 레거시 삭제

| # | 체크리스트 | 검증 방법 |
|---|-----------|----------|
| L-1 | `domain/repository/ProductRepository.java` 삭제 | 파일 없음 |
| L-2 | `domain/repository/InventoryRepository.java` 삭제 | 파일 없음 |
| L-3 | `domain/repository/ProductQueryRepository.java`, `ProductQueryRepositoryImpl.java` 삭제 | 파일 없음 |
| L-4 | `infrastructure/repository/JpaProductRepository.java`, `JpaInventoryRepository.java` 삭제 | 파일 없음 |
| L-5 | `ProductService.java`, `ProductDiscountService.java`, `ProductDiscountCacheService.java`, `ProductRankingService.java` 삭제 | 파일 없음 |
| L-6 | `InventoryService.java` 삭제 | 파일 없음 |
| L-7 | `ProductMapper.java`, `InventoryMapper.java` 삭제 | 파일 없음 |
| L-8 | `CompanyValidator.java`, `ProductValidator.java`, `PermissionValidator.java`, `InventoryValidator.java` 삭제 | 파일 없음 |
| L-9 | `InventoryRedisService.java` 삭제 (미사용) | 파일 없음 |
| L-10 | `InventoryLockTestController.java`, `InventoryTestProducerController.java` 삭제 | 파일 없음 |
| L-11 | `RedissonLockInventoryTestService.java` 삭제 | 파일 없음 |

### Section 10: 빌드

| # | 체크리스트 | 검증 방법 |
|---|-----------|----------|
| B-1 | `./gradlew :product:compileJava` 성공 | BUILD SUCCESSFUL |

---

## 12. Command / Result DTO 설계

### Product Command/Result

```java
// CreateProductCommand.java
public record CreateProductCommand(
    UUID companyId,
    String name,
    String description,
    Integer price,
    ProductCategory category,
    UUID requestUserId,
    String requestUserRole,
    UUID requestUserCompanyId
) {}

// UpdateProductCommand.java
public record UpdateProductCommand(
    UUID productId,
    String name,
    String description,
    Integer price,
    ProductCategory category,
    ProductStatus productStatus,
    UUID requestUserId,
    String requestUserRole,
    UUID requestUserCompanyId
) {}

// ProductResult.java
public record ProductResult(
    UUID productId, UUID companyId, String name, String description,
    Integer price, ProductCategory category, ProductStatus productStatus,
    boolean soldOut, LocalDateTime createdAt
) {
    public static ProductResult from(Product product, boolean soldOut) { ... }
}

// ProductPriceResult.java
public record ProductPriceResult(UUID productId, Integer currentPrice, boolean isDiscounted) {}

// PopularProductResult.java
public record PopularProductResult(
    UUID productId, String name, Integer price, String description,
    ProductCategory category, long soldCount
) {}
```

### Inventory Command/Result

```java
// InventoryResult.java
public record InventoryResult(
    UUID inventoryId, UUID productId,
    Integer quantity, Integer reservedQuantity, Integer availableQuantity,
    InventoryStatus inventoryStatus
) {
    public static InventoryResult from(Inventory inventory) { ... }
}

// CheckQuantityResult.java
public record CheckQuantityResult(UUID productId, Integer quantity, Integer availableQuantity,
                                   Integer reservedQuantity, InventoryStatus inventoryStatus) {}

// CheckOrderableResult.java
public record CheckOrderableResult(UUID productId, boolean orderable) {}
```

---

## 13. build.gradle 변경 없음

product/build.gradle은 이미 `common-lib`, `event-schema`, QueryDSL 모두 포함 — 추가 의존성 불필요.

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0 | 2026-03-01 | Initial design — hexagonal-ddd-product | product-manager |
