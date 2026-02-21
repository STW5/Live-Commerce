# Plan: hexagonal-ddd-ai

## 개요

| 항목 | 내용 |
|------|------|
| Feature | hexagonal-ddd-ai |
| 작성일 | 2026-02-19 |
| 우선순위 | P2 (지원 도메인) |
| 예상 복잡도 | 낮음 (단일 도메인, 외부 API 1개) |
| 담당 서비스 | ai (port: 19100) |

## 배경 및 목적

ai 서비스는 Gemini API를 통해 라이브 방송 채팅을 분석하고 결과를 저장하는 지원 도메인 서비스다.
현재 구조는 `AI.java` 도메인 모델에 JPA 어노테이션이 직접 적용되어 있고, `AiRepository`가 도메인 레이어에 `JpaRepository`를 상속하는 전형적인 레거시 패턴이다.

이번 마이그레이션을 통해:
- 도메인 모델의 JPA 독립성 확보
- Gemini API 호출을 Port로 추상화 (테스트 용이성)
- SlackSender를 Port로 분리 (알림 구현체 교체 가능)
- 4개 UseCase로 서비스 책임 분리

## 현재 서비스 구조 분석

### 파일 현황 (29개)

```
ai/src/main/java/com/live_commerce/ai/
├── AiApplication.java
├── application/
│   ├── dto/
│   │   ├── request/  AiAnalyzeRequestDto, AiSearchCondition, GeminiRequestDto
│   │   └── response/ AiCreateResponseDto, AiGetResponseDto, GeminiResponseDto
│   ├── exception/    AiExceptionCode, CustomException, ExceptionCode
│   └── service/      AiService  ← 단일 서비스 (4개 메서드)
├── domain/
│   ├── model/        AI, BaseEntity  ← @Entity 직접 보유
│   ├── prompt/       PromptGenerator  ← application DTO 참조 (레이어 위반)
│   └── repository/   AiRepository (extends JpaRepository), AiQueryRepository,
│                     AiQueryRepositoryImpl
├── infrastructure/
│   ├── client/       GeminiFeignClient, GeminiServiceAdapter
│   ├── common/       ResponseUtil
│   ├── config/       AuditorAwareImpl, QuerydslConfig, SecurityConfig, SwaggerConfig
│   ├── exception/    GlobalExceptionHandler
│   ├── filter/       AuthenticationFilter
│   ├── security/     RequestUserDetails
│   └── slack/        SlackSender
└── presentation/
    ├── common/       ApiResponse
    └── controller/   AiController
```

### 주요 도메인 모델

**AI** — `@Entity @Table(name="p_ai")`
- id (UUID, @UuidGenerator)
- liveBroadcastId (UUID)
- requestPayload (TEXT) — 채팅 메시지 JSON 직렬화
- responsePayload (TEXT) — Gemini 분석 결과
- 메서드: `of()`, `markAsDeleted()` (BaseEntity 상속)

### 핵심 비즈니스 로직

1. **채팅 분석 (analyze)**: 최대 50개 메시지 트림 → PromptGenerator → Gemini API → Slack 알림 → DB 저장
2. **내부 시크릿 검증**: `X-Internal-Secret` 헤더로 요청 인증 (내부 서비스 전용)
3. **권한 검증**: ROLE_MASTER만 조회/삭제 가능
4. **QueryDSL**: AiQueryRepositoryImpl — liveBroadcastId, userId 기반 검색

### 현재 문제점 (레이어 위반)

| 문제 | 위치 | 설명 |
|------|------|------|
| JPA 도메인 오염 | `AI.java` | `@Entity`, `@Table`, `@Column` |
| JPA 레포지토리 도메인 배치 | `domain/repository/` | `extends JpaRepository` |
| 레이어 위반 | `domain/prompt/PromptGenerator` | `AiAnalyzeRequestDto` (application) 참조 |
| 직접 인프라 주입 | `AiService` | `GeminiServiceAdapter`, `SlackSender` 직접 사용 |

## 마이그레이션 범위

### 포함 (In Scope)
- [x] AI 도메인 모델 JPA 분리
- [x] 인바운드 포트 (4개 UseCase 인터페이스) 생성
- [x] 아웃바운드 포트 (3개: Repository, Gemini, Notification) 생성
- [x] Result/Command DTO 생성
- [x] AiJpaEntity 어댑터 계층 생성
- [x] AiPersistenceAdapter (JPA + QueryDSL 통합)
- [x] GeminiFeignAdapter (GeminiPort 구현)
- [x] SlackNotificationAdapter (AiNotificationPort 구현)
- [x] 4개 Application Service 분리
- [x] Controller → UseCase 전환
- [x] PromptGenerator 레이어 정리 (domain → 독립 Command 입력)
- [x] 레거시 코드 삭제

### 제외 (Out of Scope)
- [ ] Gemini API 계약 변경
- [ ] DB 스키마 변경 (p_ai 테이블 유지)
- [ ] Slack 알림 내용 변경
- [ ] 내부 시크릿 검증 로직 변경

## 예상 포트 구조

### 인바운드 UseCase (4개)

- `AnalyzeUseCase` — 채팅 분석 실행 및 저장 (POST /ai)
- `GetAiAnalysisUseCase` — 분석 결과 단건 조회 (GET /ai/{id})
- `GetAiAnalysisListUseCase` — 분석 결과 목록 조회 (GET /ai/search)
- `DeleteAiAnalysisUseCase` — 분석 결과 삭제 (DELETE /ai/{id})

### 아웃바운드 포트 (3개)

- `AiRepositoryPort` — save(AiResult), findById(UUID), searchAi(condition)
- `GeminiPort` — generateText(prompt): String
- `AiNotificationPort` — sendAnalysisResult(userId, message)

### Command/Result DTO

**Command:**
- `AnalyzeCommand` — liveBroadcastId, chatMessages, secret

**Result:**
- `AiResult` — id, liveBroadcastId, requestPayload, responsePayload, createdAt

## 예상 신규 패키지 구조

```
ai/src/main/java/com/live_commerce/ai/
├── domain/
│   ├── model/          AI.java (순수 Java, JPA 제거)
│   │                   BaseEntity.java (순수 Java)
│   ├── port/
│   │   ├── in/         AnalyzeUseCase, GetAiAnalysisUseCase,
│   │   │               GetAiAnalysisListUseCase, DeleteAiAnalysisUseCase
│   │   └── out/        AiRepositoryPort, GeminiPort, AiNotificationPort
│   └── prompt/         PromptGenerator (Command 입력으로 전환)
├── application/
│   ├── dto/
│   │   ├── command/    AnalyzeCommand
│   │   ├── result/     AiResult
│   │   ├── request/    AiAnalyzeRequestDto, AiSearchCondition (유지)
│   │   └── response/   AiCreateResponseDto, AiGetResponseDto (유지)
│   ├── exception/      (유지)
│   └── service/
│       ├── AnalyzeService
│       ├── GetAiAnalysisService
│       ├── GetAiAnalysisListService
│       └── DeleteAiAnalysisService
├── adapter/
│   └── out/
│       ├── persistence/
│       │   ├── AiJpaEntity.java
│       │   ├── AiJpaRepository.java
│       │   ├── AiQueryJpaRepository.java
│       │   ├── AiQueryJpaRepositoryImpl.java
│       │   └── AiPersistenceAdapter.java
│       ├── external/
│       │   └── GeminiFeignAdapter.java
│       └── notification/
│           └── SlackNotificationAdapter.java
└── infrastructure/
    ├── client/         GeminiFeignClient, GeminiServiceAdapter (유지 또는 통합)
    ├── config/         (유지)
    ├── exception/      (유지)
    ├── filter/         (유지)
    ├── security/       (유지)
    └── slack/          SlackSender (유지)
```

## 구현 순서 (Do Phase 예정)

1. **도메인 모델 JPA 분리** — AI, BaseEntity
2. **아웃바운드 포트 생성** — AiRepositoryPort, GeminiPort, AiNotificationPort
3. **인바운드 UseCase 생성** — 4개 UseCase 인터페이스
4. **Command/Result DTO** — AnalyzeCommand, AiResult
5. **AiJpaEntity** — from(AI), toDomain() 메서드
6. **AiJpaRepository** — 새 JPA 레포지토리 (어댑터용)
7. **AiQueryJpaRepository/Impl** — QueryDSL 어댑터 이전
8. **AiPersistenceAdapter** — AiRepositoryPort 구현
9. **GeminiFeignAdapter** — GeminiPort 구현
10. **SlackNotificationAdapter** — AiNotificationPort 구현
11. **Application Services** — 4개 서비스 (포트만 주입)
12. **PromptGenerator 정리** — Command 기반 입력으로 전환
13. **Controller 전환** — UseCase 주입으로 전환
14. **레거시 삭제** — 기존 domain/repository, AiService 삭제
15. **빌드 & 테스트**

## 리스크 분석

| 리스크 | 수준 | 완화 방안 |
|--------|------|-----------|
| PromptGenerator 레이어 정리 | 낮음 | domain 내 ChatMessage 값 객체로 전환 |
| QueryDSL Q클래스 재생성 | 낮음 | AiJpaEntity 기반 QAiJpaEntity로 전환 |
| `@Transactional` 누락 | 낮음 | 기존 서비스 어노테이션 그대로 이전 |

## 성공 기준

- [ ] AI 도메인 모델에서 JPA import 제거
- [ ] 4개 Application Service가 Port 인터페이스만 주입
- [ ] Gemini API 호출이 GeminiPort를 통해서만 이루어짐
- [ ] @SpringBootTest contextLoads 통과
- [ ] Gap Analysis Match Rate ≥ 90%

## 참조

- 이전 마이그레이션 패턴: `user/`, `payment/`, `coupon/`, `order/`, `livebroadcast/`
- Gemini 클라이언트: `ai/infrastructure/client/GeminiFeignClient.java`
- SlackSender: `ai/infrastructure/slack/SlackSender.java`
