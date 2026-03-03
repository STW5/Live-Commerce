# Plan: hexagonal-ddd-product

## 개요

| 항목 | 내용 |
|------|------|
| Feature | hexagonal-ddd-product |
| 작성일 | 2026-02-18 |
| 우선순위 | P1 (핵심 도메인) |
| 예상 복잡도 | 높음 (이중 서브도메인 + 분산락 + 캐싱) |
| 담당 서비스 | product (port: 19070) |

## 배경 및 목적

product 서비스는 Live Commerce 플랫폼의 핵심 도메인으로, 상품 관리(`product`)와 재고 관리(`inventory`) 두 개의 서브도메인을 포함한다. 현재 구조는 JPA 어노테이션이 도메인 모델에 직접 적용되어 있으며, 도메인 레이어에 JPA Repository 인터페이스가 위치하는 전형적인 레거시 패턴을 따른다.

이번 마이그레이션을 통해 다른 서비스에 적용한 것과 동일한 헥사고날 아키텍처 + DDD 패턴을 적용하여:
- 도메인 모델의 JPA 독립성 확보
- 포트를 통한 의존성 역전
- 분산락(Redisson)과 캐싱(Redis)의 인프라 계층 격리

## 현재 서비스 구조 분석

### 이중 서브도메인 구조 (현행)
```
product/src/main/java/com/live_commerce/product/
├── product/                          ← 상품 서브도메인
│   ├── application/
│   │   ├── dto/                      (10+ DTO 클래스)
│   │   ├── exception/                (CustomException, ExceptionCode 등)
│   │   ├── mapper/                   (ProductMapper)
│   │   ├── service/                  (ProductService, ProductDiscountService,
│   │   │                              ProductDiscountCacheService, ProductRankingService)
│   │   └── validation/               (CompanyValidator, PermissionValidator, ProductValidator)
│   ├── domain/
│   │   ├── model/                    (Product, ProductDiscount, ProductCategory,
│   │   │                              ProductStatus, RestockNotificationRequest, BaseEntity)
│   │   ├── exception/                (ProductException)
│   │   └── repository/               (ProductRepository, ProductQueryRepository,
│   │                                  ProductQueryRepositoryImpl, ProductDiscountRepository)
│   ├── infrastructure/
│   │   ├── client/                   (CompanyClient, ExternalCompanyResponseDto)
│   │   ├── config/                   (JpaConfig, QuerydslConfig, RedisConfig, SwaggerConfig)
│   │   ├── exception/                (GlobalExceptionHandler)
│   │   ├── kafka/                    (InventoryEventConsumer, ProductSoldOutListener,
│   │   │                              events: 5개)
│   │   ├── repository/               (JpaProductRepository, JpaProductDiscountRepository)
│   │   └── security/                 (AuthFilter, FeignInterceptor 등)
│   └── presentation/
│       └── controller/               (ProductController)
└── inventory/                        ← 재고 서브도메인
    ├── application/
    │   ├── dto/                      (request 3개, response 3개, cache 1개)
    │   ├── exception/                (InventoryExceptionCode)
    │   ├── mapper/                   (InventoryMapper)
    │   ├── service/                  (InventoryService)
    │   └── validation/               (InventoryValidator)
    ├── domain/
    │   ├── model/                    (Inventory, InventoryStatus, BaseEntity)
    │   ├── exception/                (InventoryException)
    │   └── repository/               (InventoryRepository)
    ├── infrastructure/
    │   ├── config/                   (RedissonConfig)
    │   ├── redis/                    (InventoryRedisService, RedisKey)
    │   └── redisson/                 (@DistributedLock AOP)
    └── presentation/
        └── controller/               (InventoryController, InventoryLockTestController)
```

### 주요 도메인 모델

**Product** — `@Entity @Table(name="p_product", schema="products")`
- productId (UUID), companyId (UUID), name, description, price, category, productStatus
- 메서드: create(), update(dto), changeStatus(), delete()

**ProductDiscount** — `@Entity @Table(name="p_product_discount", schema="products")`
- id (Long), `@ManyToOne Product product`, discountPrice, startAt, endAt, appliedBy
- 메서드: isActiveNow()
- **특이사항**: Product와 @ManyToOne 관계 — JPA 관계 분리 필요

**Inventory** — `@Entity @Table(name="p_inventory", schema="inventories")`
- inventoryId (UUID), productId (UUID), quantity, reservedQuantity, availableQuantity, inventoryStatus
- 메서드: decrease(), increase(), discontinue(), changeStatus()
- **특이사항**: `decreaseInventoryAtomically()` — @Query JPQL 원자적 업데이트

**RestockNotificationRequest** — `@Entity` (알림 요청, 사용 여부 확인 필요)

### 핵심 비즈니스 로직

1. **Redisson 분산락 AOP**: `@DistributedLock(key="#productId")` — decrease/increase 작업
2. **Spring Cache**: `@Cacheable(value="productDetail")`, `@CacheEvict` — 상품 상세
3. **Redis Sorted Set**: ProductRankingService — top10 인기상품 (sold_count)
4. **할인가 캐시**: ProductDiscountCacheService — 할인 적용 가격 Redis 저장
5. **Kafka 이벤트**: inventory-decrease(소비) → inventory-decreased(발행) / inventory-rollback(소비) / inventory-sold-out(발행)

## 마이그레이션 범위

### 포함 (In Scope)
- [x] Product 도메인 모델 JPA 분리
- [x] ProductDiscount 도메인 모델 JPA 분리 (productId 참조로 전환)
- [x] Inventory 도메인 모델 JPA 분리
- [x] 인바운드 포트 (UseCase 인터페이스) 생성
- [x] 아웃바운드 포트 (Repository, External, Event, Cache) 생성
- [x] 14개 Application Service 분리
- [x] JpaEntity 어댑터 계층 생성
- [x] QueryDSL 어댑터 (ProductQueryJpaRepository → 새 Q클래스)
- [x] Feign 어댑터 (CompanyFeignAdapter)
- [x] Kafka 어댑터 (InventoryEventKafkaAdapter)
- [x] Redis 어댑터 (캐시, 랭킹)
- [x] 분산락 AOP 유지 + Port 래핑
- [x] Controller → UseCase 전환
- [x] 레거시 코드 삭제

### 제외 (Out of Scope)
- [ ] Kafka topic 구조 변경 (기존 토픽명 유지)
- [ ] Redis 키 패턴 변경 (기존 키 유지)
- [ ] 분산락 AOP 자체 변경
- [ ] 외부 API 계약 변경

## 예상 포트 구조

### 인바운드 UseCase (14개)

**Product 서브도메인 (8개):**
- `CreateProductUseCase` — 상품 생성
- `UpdateProductUseCase` — 상품 수정
- `DeleteProductUseCase` — 상품 삭제
- `GetProductUseCase` — 상품 단건 조회
- `SearchProductsUseCase` — 상품 검색 (QueryDSL)
- `GetProductsByIdsUseCase` — 상품 일괄 조회 (bulk)
- `GetProductPriceUseCase` — 상품 가격 조회
- `ApplyLiveDiscountUseCase` — 라이브 할인 적용

**Inventory 서브도메인 (6개):**
- `CreateInventoryUseCase` — 재고 생성
- `GetInventoryUseCase` — 재고 단건 조회
- `DecreaseInventoryUseCase` — 재고 차감 (분산락)
- `IncreaseInventoryUseCase` — 재고 복구 (분산락)
- `CheckInventoryQuantityUseCase` — 재고 수량 확인
- `CheckOrderableInventoryUseCase` — 주문 가능 재고 확인

### 아웃바운드 포트 (8개)

**Product:**
- `ProductRepositoryPort` — save, findById, existsById, findAllByIds, softDelete
- `ProductDiscountRepositoryPort` — save, findActiveByProductId, findAll
- `ProductQueryPort` — search(condition, pageable)
- `ExternalCompanyPort` — existsCompany(UUID): boolean

**Inventory:**
- `InventoryRepositoryPort` — save, findByProductId, existsByProductId, decreaseAtomically, increaseAtomically
- `InventoryEventPublisherPort` — publishDecreased, publishSoldOut
- `InventoryCachePort` — getSoldCount, incrementSoldCount (Redis)
- `ProductCachePort` — getProductDetail, evictProductDetail, getDiscountPrice, setDiscountPrice

## 주요 기술 도전 과제

### 1. ProductDiscount @ManyToOne 분리
**현재**: `@ManyToOne(fetch=LAZY) private Product product;`
**변경**: `private UUID productId;` (도메인 모델에서 ID 참조로 전환)
- JpaEntity에서만 @ManyToOne 관계 유지 또는 productId FK로 단순화
- ProductDiscountJpaEntity: `@ManyToOne Product product` 유지 또는 productId 필드로 대체

### 2. decreaseInventoryAtomically @Query 유지
**현재**: `@Modifying @Query("UPDATE Inventory SET ...")` — 원자적 update
**전략**: InventoryJpaRepository에 `@Query` 메서드 유지, InventoryRepositoryPort.decreaseAtomically() 위임

### 3. @DistributedLock AOP + Port 패턴
**현재**: `@DistributedLock(key="#productId")` 직접 InventoryService에 적용
**전략**: Application Service에서 InventoryRepositoryPort 호출 시 AOP는 Infrastructure에서 유지
- Port 메서드에 @DistributedLock 적용 불가 (인터페이스)
- Adapter 구현체에 @DistributedLock 적용하거나 별도 Lock Port 생성

### 4. @Cacheable 위치
**현재**: ProductService에 `@Cacheable(value="productDetail")`
**전략**: Application Service에서 ProductCachePort를 직접 호출하거나,
         Cache Adapter에 @Cacheable 유지

### 5. 이중 서브도메인 패키지 통합
**현재**: `product.product.*` + `product.inventory.*` 완전 분리 구조
**전략**: 새 헥사고날 구조에서도 두 서브도메인 분리 유지
```
com.live_commerce.product/
├── product/domain/port/in|out
├── product/application/service
├── product/adapter/out/persistence
├── inventory/domain/port/in|out
├── inventory/application/service
└── inventory/adapter/out/persistence
```

## 구현 순서 (Do Phase 예정)

1. **도메인 모델 JPA 분리** — BaseEntity, Product, ProductDiscount, Inventory
2. **아웃바운드 포트 생성** — 8개 포트 인터페이스
3. **인바운드 UseCase 생성** — 14개 UseCase 인터페이스
4. **Result/Command DTO** — ProductResult, InventoryResult, CreateProductCommand 등
5. **JpaEntity 계층** — ProductJpaEntity, ProductDiscountJpaEntity, InventoryJpaEntity
6. **JpaRepository** — 새 JPA 레포지토리 (어댑터용)
7. **Persistence Adapter** — ProductPersistenceAdapter, InventoryPersistenceAdapter 등
8. **QueryDSL Adapter** — ProductQueryAdapter (QProductJpaEntity 사용)
9. **External/Event Adapter** — CompanyFeignAdapter, InventoryKafkaAdapter, Redis 어댑터
10. **Application Services** — 14개 서비스 (포트만 주입)
11. **Controller 전환** — ProductController, InventoryController → UseCase 주입
12. **레거시 삭제** — 기존 repository, mapper, validator 삭제
13. **빌드 & 테스트**

## 리스크 분석

| 리스크 | 수준 | 완화 방안 |
|--------|------|-----------|
| @DistributedLock AOP와 Port 패턴 충돌 | 높음 | Adapter 구현체에 직접 적용 |
| decreaseAtomically @Query 마이그레이션 | 중간 | JpaRepository에 @Query 유지 |
| ProductDiscount @ManyToOne 분리 | 중간 | productId 참조로 전환 |
| 이중 서브도메인 패키지 충돌 | 낮음 | 기존 분리 구조 그대로 유지 |
| Redis 캐시 어노테이션 위치 변경 | 낮음 | Port 방식으로 대체 |
| KafkaTemplate 직접 사용 제거 | 낮음 | InventoryEventPublisherPort로 래핑 |

## 성공 기준

- [ ] 모든 도메인 모델에서 JPA import 제거
- [ ] 14개 Application Service가 Port 인터페이스만 주입
- [ ] 분산락 정상 동작 확인 (재고 차감 동시성)
- [ ] @SpringBootTest contextLoads 통과
- [ ] Gap Analysis Match Rate ≥ 90%

## 참조

- 이전 마이그레이션 패턴: `user/`, `payment/`, `coupon/`, `order/`, `livebroadcast/`
- 최고 참조 사례: `hexagonal-ddd-livebroadcast` (96.3%) — 아카이브됨
- Redisson 설정: `inventory/infrastructure/config/RedissonConfig.java`
- Kafka 이벤트: `product/infrastructure/kafka/event/*.java` (5개 이벤트)
