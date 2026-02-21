# Plan: 나머지 서비스 헥사고날 DDD 적용 우선순위 계획

> **Feature**: hexagonal-ddd-remaining-services
> **작성일**: 2026-02-18
> **작성자**: PDCA Plan Phase
> **참조**: hexagonal-ddd-order (90.4%), hexagonal-ddd-user (94.4%), hexagonal-ddd-payment (94.1%), hexagonal-ddd-coupon (92.1%)

---

## 1. 현황 분석

### 완료된 서비스
| 서비스 | Match Rate | 완료일 |
|--------|:----------:|--------|
| user | 94.4% | 2026-02-14 (archived) |
| payment | 94.1% | 2026-02-14 (archived) |
| coupon | 92.1% | 2026-02-14 (archived) |
| order | 90.4% | 2026-02-15 (archived) |

### 미적용 서비스 현황 스캔 결과

| 서비스 | 파일 수 | port/in | port/out | adapter | Kafka | Feign | 도메인 JPA |
|--------|:-------:|:-------:|:--------:|:-------:|:-----:|:-----:|:---------:|
| **product** | 88 | 0 | 0 | 0 | ✅ (2) | ✅ | ✅ (Inventory.java) |
| **livebroadcast** | 76 | 0 | 0 | 0 | ✅ | ✅ (3) | 확인필요 |
| **chat** | 45 | 0 | 0 | 0 | - | - | ✅ |
| **notification** | 31 | 0 | 0 | 0 | ✅ (2) | ✅ (1) | ✅ (Notification.java) |
| **ai** | 30 | 0 | 0 | 0 | - | - | ✅ |
| **company** | 26 | 0 | 0 | 0 | - | - | ✅ |

---

## 2. 우선순위 결정 기준

| 기준 | 가중치 | 설명 |
|------|:------:|------|
| 비즈니스 임팩트 | 40% | Saga 패턴, 분산 트랜잭션 관련도 |
| 아키텍처 위반 심각도 | 30% | 도메인 JPA 오염, 서비스 레이어 인프라 직접 의존 |
| 복잡도 (파일수, 외부 연동) | 20% | 파일 수, Kafka/Feign 연동 수 |
| 타 서비스 의존도 | 10% | 다른 서비스가 이 서비스를 호출하는 빈도 |

---

## 3. 서비스별 상세 분석 및 우선순위

### 🔴 P1 - PRODUCT 서비스 (최우선)

**우선순위 이유**:
- **Saga 핵심 참여자**: Order Saga에서 재고 감소(`inventory-decrease`)와 롤백(`inventory-rollback`) 담당
- **분산 락 사용**: Redisson `@DistributedLock` AOP — 아키텍처 침해 심각
- **가장 많은 파일** (88개): 두 서브모듈 구조 (`product/product/`, `product/inventory/`)
- **InventoryService 위반 사항**:
  - `InventoryRepository` (도메인 레이어) 직접 주입
  - `KafkaTemplate` 직접 주입 (인프라 의존)
  - `StringRedisTemplate` 직접 주입
  - `ProductRepository` 크로스 도메인 접근

**주요 리스크**:
- 두 서브모듈 (`product/product`, `product/inventory`) 간 경계 정리 필요
- `@DistributedLock` AOP 어댑터화 방법 결정 필요
- Kafka consumer/producer 어댑터화 (InventoryEventConsumer, ProductSoldOutListener)

**예상 소요**: 3-4일, Design Match 목표: 90%+

---

### 🟡 P2 - LIVEBROADCAST 서비스 (진행 중)

**현재 상태**: design 단계 (`docs/02-design/features/hexagonal-ddd-livebroadcast.design.md` 존재)

**우선순위 이유**:
- **이미 진행 중** → 중단하면 문맥 손실
- 다른 서비스의 중요 의존 대상 (OrderService, NotificationService에서 Feign 호출)
- WebSocket 아키텍처 분리 필요 (별도 adapter/in 구성)
- 스케줄러 → 어댑터화 필요

**주요 리스크**:
- WebSocket Handler를 adapter/in으로 어떻게 정의할지
- Scheduler와 Kafka 이벤트 발행의 경계 설계

**예상 소요**: 2-3일, Design Match 목표: 90%+

---

### 🟡 P3 - NOTIFICATION 서비스 (중간)

**우선순위 이유**:
- `Notification.java` 도메인 모델에 `@Entity`, `@Table` 직접 선언 (심각한 아키텍처 위반)
- Kafka consumer 존재 (알림 이벤트 처리)
- 상대적으로 파일 수 적음 (31개) → 빠른 마이그레이션 가능
- 외부 Slack/SMTP 통합 → 어댑터화 좋은 사례

**주요 리스크**:
- Slack, SMTP 클라이언트를 어떤 포트로 추상화할지
- 알림 재시도 로직 도메인 vs 인프라 경계 결정

**예상 소요**: 1-2일, Design Match 목표: 92%+

---

### 🟢 P4 - CHAT 서비스 (중간-낮음)

**우선순위 이유**:
- WebSocket + Redis Pub/Sub 특수 아키텍처
- 비즈니스 트랜잭션 복잡도 낮음 (메시지 저장/조회)
- Kafka 없음, Feign 없음

**주요 리스크**:
- WebSocket Handler를 adapter/in으로 정의하는 패턴 설계
- Redis Pub/Sub을 outbound port로 추상화
- 실시간 특성 유지하면서 포트 추상화

**예상 소요**: 2일, Design Match 목표: 90%+

---

### 🟢 P5 - COMPANY 서비스 (낮음)

**우선순위 이유**:
- 단순 CRUD 서비스 (26개 파일)
- Kafka 없음, 외부 연동 최소
- 비즈니스 중요도 낮음

**예상 소요**: 1일, Design Match 목표: 92%+

---

### 🔵 P6 - AI 서비스 (낮음)

**우선순위 이유**:
- 외부 API(Gemini) 래퍼 역할
- 단순한 비즈니스 로직
- 독립적 서비스 (타 서비스 의존 없음)

**예상 소요**: 1일, Design Match 목표: 92%+

---

## 4. 권장 실행 순서

```
[현재] livebroadcast (design → do → check)
   ↓
[P1] product (plan → design → do → check)
   ↓
[P3] notification (plan → design → do → check)
   ↓
[P4] chat (plan → design → do → check)
   ↓
[P5] company + [P6] ai (병렬 진행 가능)
```

---

## 5. Product 서비스 주요 마이그레이션 포인트

product 서비스가 가장 복잡하므로 사전 분석:

### 두 서브모듈 경계 정리
```
product/product/     → ProductService, ProductRepository
product/inventory/   → InventoryService, InventoryRepository
```
- 현재: 크로스 모듈 직접 접근 (`InventoryService`에서 `ProductRepository` 접근)
- 목표: Port 인터페이스를 통한 크로스 모듈 통신

### 예상 Port 구조 (product 서브모듈)
```
domain/port/in/
  - GetProductUseCase
  - CreateProductUseCase
  - UpdateProductUseCase
  - DeleteProductUseCase

domain/port/out/
  - ProductRepositoryPort
  - InventoryQueryPort (product → inventory 조회)
  - CompanyQueryPort (feign → port)
```

### 예상 Port 구조 (inventory 서브모듈)
```
domain/port/in/
  - DecreaseInventoryUseCase
  - IncreaseInventoryUseCase
  - CheckInventoryUseCase

domain/port/out/
  - InventoryRepositoryPort
  - InventoryEventPublisherPort (KafkaTemplate → port)
  - CachePort (Redis → port)
  - LockPort (Redisson → port)
```

---

## 6. 공통 마이그레이션 패턴 (기존 완료 서비스 참조)

기존 완료된 서비스에서 확립된 패턴:

| 요소 | 기존 패턴 | 적용 |
|------|-----------|------|
| JPA Entity 분리 | `XxxJpaEntity.from(domain)` + `toDomain()` | 모든 서비스 |
| UseCase 명명 | `{Verb}{Noun}UseCase` | 모든 서비스 |
| Port 명명 | `{Noun}Port`, `{Noun}QueryPort` | 모든 서비스 |
| Result DTO | `{Noun}Result` record | 모든 서비스 |
| Command DTO | `{Verb}{Noun}Command` record | 모든 서비스 |
| Adapter 명명 | `{Type}{Noun}Adapter` | 모든 서비스 |

---

## 7. 성공 기준

| 항목 | 기준 |
|------|------|
| Design Match Rate | ≥ 90% |
| 도메인 모델 JPA 순수성 | domain/model/*.java에 @Entity 없음 |
| 서비스 레이어 순수성 | application/service/*.java에 Feign/Kafka/JPA import 없음 |
| Port 인터페이스 순수성 | port/*.java에 Spring Data 최소화 (Pageable 예외) |
| Build | SUCCESS |
| Test | PASS |

---

## 8. 리스크 및 대응

| 리스크 | 심각도 | 대응 방안 |
|--------|:------:|----------|
| Product 두 서브모듈 의존성 | HIGH | port/in, port/out으로 크로스 접근 추상화 |
| Redisson Lock 어댑터화 | MEDIUM | LockPort 인터페이스 + RedissonLockAdapter |
| WebSocket Handler 어댑터화 | MEDIUM | adapter/in/websocket 패키지 신설 |
| Chat Redis Pub/Sub | MEDIUM | MessageBusPort 추상화 |

---

## 9. 다음 단계

**즉시**: `/pdca design hexagonal-ddd-livebroadcast` — livebroadcast 설계 확인 후 do 단계
**이후**: `/pdca plan hexagonal-ddd-product` — product 서비스 상세 플랜 수립

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-02-18 | Initial plan — 6개 서비스 현황 분석 및 우선순위 수립 |
