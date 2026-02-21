# Design: hexagonal-ddd-ai

## 개요

| 항목 | 내용 |
|------|------|
| Feature | hexagonal-ddd-ai |
| 작성일 | 2026-02-19 |
| 참조 Plan | docs/01-plan/features/hexagonal-ddd-ai.plan.md |
| 참조 패턴 | hexagonal-ddd-user (94.4%), hexagonal-ddd-livebroadcast (96.3%) |

---

## 1. 아키텍처 레이어 다이어그램

```
┌─────────────────────────────────────────────────────────────┐
│  Presentation Layer                                         │
│  AiController                                               │
│  → AnalyzeUseCase / GetAiAnalysisUseCase /                 │
│    GetAiAnalysisListUseCase / DeleteAiAnalysisUseCase       │
└────────────────────┬────────────────────────────────────────┘
                     │ implements
┌────────────────────▼────────────────────────────────────────┐
│  Application Layer                                          │
│  AnalyzeService / GetAiAnalysisService /                    │
│  GetAiAnalysisListService / DeleteAiAnalysisService         │
│                                                             │
│  → AiRepositoryPort (out)                                   │
│  → GeminiPort (out)                                         │
│  → AiNotificationPort (out)                                 │
└──────┬────────────────────────────────────────┬─────────────┘
       │ domain model                           │ port interface
┌──────▼─────────────┐           ┌─────────────▼──────────────┐
│  Domain Layer       │           │  Adapter Layer (out)        │
│  AI (pure Java)     │           │  AiPersistenceAdapter       │
│  BaseEntity         │           │  GeminiFeignAdapter         │
│  PromptGenerator    │           │  SlackNotificationAdapter   │
│  port/in/*.java     │           │                             │
│  port/out/*.java    │           │                             │
└─────────────────────┘           └─────────────────────────────┘
```

---

## 2. 도메인 모델 설계

### 2-1. AI (순수 Java 도메인 모델)

**파일**: `domain/model/AI.java`

```java
public class AI {
    private UUID id;
    private UUID liveBroadcastId;
    private String requestPayload;   // 채팅 메시지 JSON
    private String responsePayload;  // Gemini 분석 결과

    // BaseEntity 필드 (순수 Java)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean deletedStatus;
    private LocalDateTime deletedAt;
    private String createdBy;
    private String updatedBy;
    private String deletedBy;

    // 정적 팩토리 메서드
    public static AI of(UUID id, UUID liveBroadcastId,
                        String requestPayload, String responsePayload) { ... }

    // 소프트 삭제
    public void markAsDeleted(String deletedBy) { ... }
}
```

**변경 포인트:**
- `@Entity`, `@Table`, `@Column` 제거
- `@Id`, `@UuidGenerator` 제거 → id는 Adapter에서 생성 후 주입
- BaseEntity의 JPA Auditing 어노테이션 모두 제거
- `from(AiJpaEntity)` 방향은 AiJpaEntity 쪽에서만 관리 (`toDomain()`)

### 2-2. BaseEntity (순수 Java)

**파일**: `domain/model/BaseEntity.java`

```java
public abstract class BaseEntity {
    protected LocalDateTime createdAt;
    protected LocalDateTime updatedAt;
    protected boolean deletedStatus = false;
    protected LocalDateTime deletedAt;
    protected String createdBy;
    protected String updatedBy;
    protected String deletedBy;

    public void markAsDeleted(String deletedBy) {
        this.deletedStatus = true;
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
    }
}
```

---

## 3. 포트 설계

### 3-1. 인바운드 포트 (UseCase)

**파일**: `domain/port/in/`

```java
// AnalyzeUseCase.java
public interface AnalyzeUseCase {
    AiResult analyze(AnalyzeCommand command);
}

// GetAiAnalysisUseCase.java
public interface GetAiAnalysisUseCase {
    AiResult getAiAnalysis(UUID id, RequestUserDetails userDetails);
}

// GetAiAnalysisListUseCase.java
public interface GetAiAnalysisListUseCase {
    Page<AiResult> getAiAnalysisList(AiSearchCondition condition,
                                     Pageable pageable,
                                     RequestUserDetails userDetails);
}

// DeleteAiAnalysisUseCase.java
public interface DeleteAiAnalysisUseCase {
    void deleteAiAnalysis(UUID id, RequestUserDetails userDetails);
}
```

### 3-2. 아웃바운드 포트

**파일**: `domain/port/out/`

```java
// AiRepositoryPort.java
public interface AiRepositoryPort {
    AiResult save(AI ai);
    Optional<AI> findById(UUID id);
    List<AI> searchAi(AiSearchCondition condition);
}

// GeminiPort.java
public interface GeminiPort {
    String generateText(String prompt);
}

// AiNotificationPort.java
public interface AiNotificationPort {
    void sendAnalysisResult(String userId, String message);
}
```

---

## 4. Command / Result DTO 설계

**파일**: `application/dto/command/`, `application/dto/result/`

```java
// AnalyzeCommand.java
public record AnalyzeCommand(
    UUID liveBroadcastId,
    List<String> messages,    // ChatMessage.message() 추출값
    String secret
) {}

// AiResult.java
public record AiResult(
    UUID id,
    UUID liveBroadcastId,
    String requestPayload,
    String responsePayload,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String createdBy,
    String updatedBy
) {
    public static AiResult from(AI ai) {
        return new AiResult(
            ai.getId(), ai.getLiveBroadcastId(),
            ai.getRequestPayload(), ai.getResponsePayload(),
            ai.getCreatedAt(), ai.getUpdatedAt(),
            ai.getCreatedBy(), ai.getUpdatedBy()
        );
    }
}
```

---

## 5. Application Service 설계

### 5-1. AnalyzeService

**파일**: `application/service/AnalyzeService.java`

```java
@Service
@RequiredArgsConstructor
public class AnalyzeService implements AnalyzeUseCase {

    private final AiRepositoryPort aiRepositoryPort;
    private final GeminiPort geminiPort;
    private final AiNotificationPort aiNotificationPort;
    private final PromptGenerator promptGenerator;
    private final ObjectMapper objectMapper;

    @Value("${internal.secret}")
    private String internalSecret;
    @Value("${slack.admin-user-id}")
    private String adminSlackUserId;

    private static final int MAX_CHAT_MESSAGES = 50;

    @Override
    public AiResult analyze(AnalyzeCommand command) {
        // 1. 내부 시크릿 검증
        if (!internalSecret.equals(command.secret())) {
            throw new CustomException(AiExceptionCode.UNAUTHORIZED_INTERNAL_REQUEST);
        }

        // 2. 메시지 트림 (최대 50개)
        List<String> messages = command.messages();
        List<String> trimmed = messages.size() > MAX_CHAT_MESSAGES
            ? messages.subList(messages.size() - MAX_CHAT_MESSAGES, messages.size())
            : messages;

        // 3. 프롬프트 생성 → Gemini API 호출
        String prompt = promptGenerator.generate(trimmed);
        String response = geminiPort.generateText(prompt);

        // 4. 요청 JSON 직렬화
        String requestPayloadJson = serializeMessages(command);

        // 5. Slack 알림
        aiNotificationPort.sendAnalysisResult(adminSlackUserId,
            "채팅 분석 완료\n\n" + response);

        // 6. 저장 및 반환
        AI ai = AI.of(command.liveBroadcastId(), requestPayloadJson, response);
        return aiRepositoryPort.save(ai);
    }

    private String serializeMessages(AnalyzeCommand command) {
        try {
            return objectMapper.writeValueAsString(command);
        } catch (Exception e) {
            throw new CustomException(AiExceptionCode.SERIALIZATION_ERROR);
        }
    }
}
```

### 5-2. GetAiAnalysisService

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetAiAnalysisService implements GetAiAnalysisUseCase {

    private final AiRepositoryPort aiRepositoryPort;

    @Override
    public AiResult getAiAnalysis(UUID id, RequestUserDetails userDetails) {
        validateMasterRole(userDetails);
        AI ai = aiRepositoryPort.findById(id)
            .orElseThrow(() -> new CustomException(AiExceptionCode.ANALYSIS_NOT_FOUND));
        return AiResult.from(ai);
    }
}
```

### 5-3. GetAiAnalysisListService

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetAiAnalysisListService implements GetAiAnalysisListUseCase {

    private final AiRepositoryPort aiRepositoryPort;

    @Override
    public Page<AiResult> getAiAnalysisList(AiSearchCondition condition,
                                             Pageable pageable,
                                             RequestUserDetails userDetails) {
        validateMasterRole(userDetails);
        // pageSize 유효성: 10/30/50만 허용
        if (!Set.of(10, 30, 50).contains(pageable.getPageSize())) {
            pageable = PageRequest.of(pageable.getPageNumber(), 10, pageable.getSort());
        }

        List<AI> ais = aiRepositoryPort.searchAi(condition);
        List<AiResult> results = ais.stream().map(AiResult::from).toList();
        return new PageImpl<>(results, pageable, results.size());
    }
}
```

### 5-4. DeleteAiAnalysisService

```java
@Service
@RequiredArgsConstructor
@Transactional
public class DeleteAiAnalysisService implements DeleteAiAnalysisUseCase {

    private final AiRepositoryPort aiRepositoryPort;

    @Override
    public void deleteAiAnalysis(UUID id, RequestUserDetails userDetails) {
        validateMasterRole(userDetails);
        AI ai = aiRepositoryPort.findById(id)
            .orElseThrow(() -> new CustomException(AiExceptionCode.ANALYSIS_NOT_FOUND));
        ai.markAsDeleted(userDetails.getUsername());
        aiRepositoryPort.save(ai);
    }
}
```

---

## 6. 어댑터 설계

### 6-1. AiJpaEntity

**파일**: `adapter/out/persistence/AiJpaEntity.java`

```java
@Entity
@Table(name = "p_ai")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiJpaEntity extends BaseJpaEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private UUID liveBroadcastId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String requestPayload;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String responsePayload;

    // JpaEntity → Domain Model
    public AI toDomain() {
        return AI.of(this.id, this.liveBroadcastId,
                     this.requestPayload, this.responsePayload,
                     this.getCreatedAt(), this.getUpdatedAt(),
                     this.isDeletedStatus(), this.getDeletedAt(),
                     this.getCreatedBy(), this.getUpdatedBy(), this.getDeletedBy());
    }

    // Domain Model → JpaEntity
    public static AiJpaEntity from(AI ai) {
        AiJpaEntity entity = new AiJpaEntity();
        entity.id = ai.getId();  // null이면 @UuidGenerator가 생성
        entity.liveBroadcastId = ai.getLiveBroadcastId();
        entity.requestPayload = ai.getRequestPayload();
        entity.responsePayload = ai.getResponsePayload();
        return entity;
    }
}
```

**파일**: `adapter/out/persistence/BaseJpaEntity.java`

```java
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseJpaEntity {

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private boolean deletedStatus = false;

    private LocalDateTime deletedAt;

    @CreatedBy
    @Column(updatable = false)
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;

    private String deletedBy;

    // 소프트 삭제 (삭제 어댑터에서 사용)
    public void applyDeletion(String deletedBy) {
        this.deletedStatus = true;
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
    }
}
```

### 6-2. AiJpaRepository / AiQueryJpaRepository

**파일**: `adapter/out/persistence/AiJpaRepository.java`

```java
public interface AiJpaRepository extends JpaRepository<AiJpaEntity, UUID> {
}
```

**파일**: `adapter/out/persistence/AiQueryJpaRepository.java`

```java
public interface AiQueryJpaRepository {
    List<AiJpaEntity> searchAi(AiSearchCondition condition);
}
```

**파일**: `adapter/out/persistence/AiQueryJpaRepositoryImpl.java`

```java
@RequiredArgsConstructor
public class AiQueryJpaRepositoryImpl implements AiQueryJpaRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<AiJpaEntity> searchAi(AiSearchCondition condition) {
        QAiJpaEntity ai = QAiJpaEntity.aiJpaEntity;
        BooleanBuilder builder = new BooleanBuilder();

        if (condition.liveBroadcastId() != null)
            builder.and(ai.liveBroadcastId.eq(condition.liveBroadcastId()));
        if (condition.createdFrom() != null)
            builder.and(ai.createdAt.goe(condition.createdFrom()));
        if (condition.createdTo() != null)
            builder.and(ai.createdAt.loe(condition.createdTo()));

        builder.and(ai.deletedStatus.isFalse());

        return queryFactory.selectFrom(ai).where(builder)
            .orderBy(ai.createdAt.desc())
            .fetch();
    }
}
```

### 6-3. AiPersistenceAdapter

**파일**: `adapter/out/persistence/AiPersistenceAdapter.java`

```java
@Component
@RequiredArgsConstructor
public class AiPersistenceAdapter implements AiRepositoryPort {

    private final AiJpaRepository aiJpaRepository;
    private final AiQueryJpaRepository aiQueryJpaRepository;

    @Override
    public AiResult save(AI ai) {
        AiJpaEntity entity = AiJpaEntity.from(ai);
        AiJpaEntity saved = aiJpaRepository.save(entity);
        return AiResult.from(saved.toDomain());
    }

    @Override
    public Optional<AI> findById(UUID id) {
        return aiJpaRepository.findById(id)
            .filter(e -> !e.isDeletedStatus())
            .map(AiJpaEntity::toDomain);
    }

    @Override
    public List<AI> searchAi(AiSearchCondition condition) {
        return aiQueryJpaRepository.searchAi(condition)
            .stream()
            .map(AiJpaEntity::toDomain)
            .toList();
    }
}
```

### 6-4. GeminiFeignAdapter

**파일**: `adapter/out/external/GeminiFeignAdapter.java`

```java
@Component
@RequiredArgsConstructor
public class GeminiFeignAdapter implements GeminiPort {

    private final GeminiServiceAdapter geminiServiceAdapter;  // 기존 Feign 래퍼 재사용

    @Override
    public String generateText(String prompt) {
        try {
            return geminiServiceAdapter.generateText(prompt);
        } catch (Exception e) {
            throw new CustomException(AiExceptionCode.GEMINI_API_ERROR);
        }
    }
}
```

> **설계 결정**: `GeminiServiceAdapter`는 Feign 설정(`@Value`, `@Component`)을 그대로 유지하고, `GeminiFeignAdapter`가 Port를 구현하여 예외 변환만 담당한다.

### 6-5. SlackNotificationAdapter

**파일**: `adapter/out/notification/SlackNotificationAdapter.java`

```java
@Component
@RequiredArgsConstructor
public class SlackNotificationAdapter implements AiNotificationPort {

    private final SlackSender slackSender;  // 기존 SlackSender 재사용

    @Override
    public void sendAnalysisResult(String userId, String message) {
        slackSender.sendMessage(userId, message);
    }
}
```

---

## 7. PromptGenerator 레이어 정리

### 변경 전 (레이어 위반)

```java
// domain/prompt/PromptGenerator.java
public String generate(List<AiAnalyzeRequestDto.ChatMessage> messages) { ... }
// → application DTO를 domain에서 import → 레이어 위반
```

### 변경 후 (순수 도메인)

```java
// domain/prompt/PromptGenerator.java
public String generate(List<String> messages) {  // 단순 String 리스트
    StringBuilder prompt = new StringBuilder();
    prompt.append("다음은 실시간 라이브 방송 중 고객들이 남긴 채팅 메시지 목록입니다.\n\n");
    for (String msg : messages) {
        prompt.append("- ").append(msg).append("\n");
    }
    // ... 나머지 동일
}
```

**AnalyzeService에서 변환 책임:**
```java
// AnalyzeCommand.messages()는 이미 List<String>
String prompt = promptGenerator.generate(command.messages());
```

**AiController에서 Command 생성 시 변환:**
```java
AnalyzeCommand command = new AnalyzeCommand(
    request.live_broadcast_id(),
    request.request_payload().chat_messages().stream()
        .map(AiAnalyzeRequestDto.ChatMessage::message)
        .toList(),
    secret
);
```

---

## 8. Controller 전환 설계

**파일**: `presentation/controller/AiController.java`

```java
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AnalyzeUseCase analyzeUseCase;
    private final GetAiAnalysisUseCase getAiAnalysisUseCase;
    private final GetAiAnalysisListUseCase getAiAnalysisListUseCase;
    private final DeleteAiAnalysisUseCase deleteAiAnalysisUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<AiCreateResponseDto>> analyze(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestBody AiAnalyzeRequestDto request) {

        AnalyzeCommand command = new AnalyzeCommand(
            request.live_broadcast_id(),
            request.request_payload().chat_messages().stream()
                .map(AiAnalyzeRequestDto.ChatMessage::message).toList(),
            secret
        );
        AiResult result = analyzeUseCase.analyze(command);
        return ResponseUtil.success(AiCreateResponseDto.from(result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<ApiResponse<AiGetResponseDto>> getAiAnalysis(
            @PathVariable UUID id,
            @AuthenticationPrincipal RequestUserDetails userDetails) {
        AiResult result = getAiAnalysisUseCase.getAiAnalysis(id, userDetails);
        return ResponseUtil.success(AiGetResponseDto.from(result));
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<ApiResponse<Page<AiGetResponseDto>>> getAiAnalysisList(
            @ModelAttribute AiSearchCondition condition,
            Pageable pageable,
            @AuthenticationPrincipal RequestUserDetails userDetails) {
        Page<AiResult> results = getAiAnalysisListUseCase.getAiAnalysisList(condition, pageable, userDetails);
        return ResponseUtil.success(results.map(AiGetResponseDto::from));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<ApiResponse<Void>> deleteAiAnalysis(
            @PathVariable UUID id,
            @AuthenticationPrincipal RequestUserDetails userDetails) {
        deleteAiAnalysisUseCase.deleteAiAnalysis(id, userDetails);
        return ResponseUtil.noContent();
    }
}
```

**DTO 변경사항:**

- `AiCreateResponseDto.from(AI)` → `AiCreateResponseDto.from(AiResult)`
- `AiGetResponseDto.from(AI)` → `AiGetResponseDto.from(AiResult)`

---

## 9. 최종 패키지 구조

```
ai/src/main/java/com/live_commerce/ai/
│
├── AiApplication.java
│
├── domain/
│   ├── model/
│   │   ├── AI.java                      ← JPA 제거, 순수 Java
│   │   └── BaseEntity.java              ← JPA 제거, 순수 Java
│   ├── port/
│   │   ├── in/
│   │   │   ├── AnalyzeUseCase.java
│   │   │   ├── GetAiAnalysisUseCase.java
│   │   │   ├── GetAiAnalysisListUseCase.java
│   │   │   └── DeleteAiAnalysisUseCase.java
│   │   └── out/
│   │       ├── AiRepositoryPort.java
│   │       ├── GeminiPort.java
│   │       └── AiNotificationPort.java
│   └── prompt/
│       └── PromptGenerator.java         ← List<String> 입력으로 변경
│
├── application/
│   ├── dto/
│   │   ├── command/
│   │   │   └── AnalyzeCommand.java      ← NEW
│   │   ├── result/
│   │   │   └── AiResult.java            ← NEW
│   │   ├── request/
│   │   │   ├── AiAnalyzeRequestDto.java  (유지)
│   │   │   ├── AiSearchCondition.java    (유지)
│   │   │   └── GeminiRequestDto.java     (유지)
│   │   └── response/
│   │       ├── AiCreateResponseDto.java  (from(AiResult) 수정)
│   │       ├── AiGetResponseDto.java     (from(AiResult) 수정)
│   │       └── GeminiResponseDto.java    (유지)
│   ├── exception/                        (유지)
│   └── service/
│       ├── AnalyzeService.java          ← NEW (implements AnalyzeUseCase)
│       ├── GetAiAnalysisService.java    ← NEW
│       ├── GetAiAnalysisListService.java← NEW
│       └── DeleteAiAnalysisService.java ← NEW
│
├── adapter/
│   └── out/
│       ├── persistence/
│       │   ├── BaseJpaEntity.java       ← NEW
│       │   ├── AiJpaEntity.java         ← NEW (from domain/model/AI)
│       │   ├── AiJpaRepository.java     ← NEW
│       │   ├── AiQueryJpaRepository.java← NEW
│       │   ├── AiQueryJpaRepositoryImpl.java ← NEW (from domain/repository)
│       │   └── AiPersistenceAdapter.java← NEW (implements AiRepositoryPort)
│       ├── external/
│       │   └── GeminiFeignAdapter.java  ← NEW (implements GeminiPort)
│       └── notification/
│           └── SlackNotificationAdapter.java ← NEW (implements AiNotificationPort)
│
├── infrastructure/
│   ├── client/
│   │   ├── GeminiFeignClient.java        (유지)
│   │   └── GeminiServiceAdapter.java     (유지 — GeminiFeignAdapter가 위임)
│   ├── common/
│   │   └── ResponseUtil.java             (유지)
│   ├── config/
│   │   ├── AuditorAwareImpl.java         (유지)
│   │   ├── QuerydslConfig.java           (유지)
│   │   ├── SecurityConfig.java           (유지)
│   │   └── SwaggerConfig.java            (유지)
│   ├── exception/
│   │   └── GlobalExceptionHandler.java   (유지)
│   ├── filter/
│   │   └── AuthenticationFilter.java     (유지)
│   ├── security/
│   │   └── RequestUserDetails.java       (유지)
│   └── slack/
│       └── SlackSender.java              (유지 — SlackNotificationAdapter가 위임)
│
└── presentation/
    ├── common/
    │   └── ApiResponse.java              (유지)
    └── controller/
        └── AiController.java             ← UseCase 주입으로 전환

```

---

## 10. 삭제 대상 (레거시)

| 파일 | 삭제 이유 |
|------|-----------|
| `domain/model/AI.java` (구버전) | AiJpaEntity로 대체 |
| `domain/model/BaseEntity.java` (구버전) | BaseJpaEntity로 대체 |
| `domain/repository/AiRepository.java` | AiJpaRepository로 대체 |
| `domain/repository/AiQueryRepository.java` | AiQueryJpaRepository로 대체 |
| `domain/repository/AiQueryRepositoryImpl.java` | AiQueryJpaRepositoryImpl로 대체 |
| `application/service/AiService.java` | 4개 서비스로 분리 |

---

## 11. 테스트 설계

### 11-1. AiApplicationTests (Context Load)

```java
@SpringBootTest
class AiApplicationTests {
    @Test
    void contextLoads() {}
}
```

**application-test.yml 추가:**
```yaml
management:
  health:
    mail:
      enabled: false
```

### 11-2. AnalyzeServiceTest

```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnalyzeServiceTest {

    @Autowired
    private AnalyzeUseCase analyzeUseCase;

    @MockitoBean
    private AiRepositoryPort aiRepositoryPort;

    @MockitoBean
    private GeminiPort geminiPort;

    @MockitoBean
    private AiNotificationPort aiNotificationPort;

    @MockitoBean
    private PromptGenerator promptGenerator;

    @Test
    @DisplayName("내부 시크릿 불일치 → UNAUTHORIZED_INTERNAL_REQUEST")
    void analyze_invalidSecret() { ... }

    @Test
    @DisplayName("50개 초과 메시지 트림 → 최근 50개만 처리")
    void analyze_trimMessages() { ... }

    @Test
    @DisplayName("Gemini API 오류 → GEMINI_API_ERROR 예외")
    void analyze_geminiFailure() { ... }
}
```

### 11-3. GetAiAnalysisServiceTest

```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GetAiAnalysisServiceTest {

    @Autowired
    private GetAiAnalysisUseCase getAiAnalysisUseCase;

    @MockitoBean
    private AiRepositoryPort aiRepositoryPort;

    @Test
    @DisplayName("ROLE_MASTER가 아닌 경우 → FORBIDDEN")
    void getAiAnalysis_forbidden() { ... }

    @Test
    @DisplayName("존재하지 않는 ID → ANALYSIS_NOT_FOUND")
    void getAiAnalysis_notFound() { ... }
}
```

---

## 12. 구현 체크리스트

### Phase 1: 도메인 계층
- [ ] `AI.java` JPA 제거 (순수 Java, 생성자에 전체 필드 포함)
- [ ] `BaseEntity.java` JPA 제거 (순수 Java)
- [ ] `domain/port/in/` — 4개 UseCase 인터페이스
- [ ] `domain/port/out/` — 3개 Port 인터페이스
- [ ] `PromptGenerator.java` — `List<String>` 입력으로 변경

### Phase 2: Application Command/Result
- [ ] `AnalyzeCommand.java`
- [ ] `AiResult.java` (with `from(AI)`)
- [ ] `AiCreateResponseDto.from(AiResult)` 수정
- [ ] `AiGetResponseDto.from(AiResult)` 수정

### Phase 3: 어댑터 계층
- [ ] `BaseJpaEntity.java` (JPA Auditing 보유)
- [ ] `AiJpaEntity.java` (`from(AI)`, `toDomain()`)
- [ ] `AiJpaRepository.java`
- [ ] `AiQueryJpaRepository.java`
- [ ] `AiQueryJpaRepositoryImpl.java` (QAiJpaEntity 사용)
- [ ] `AiPersistenceAdapter.java` (implements AiRepositoryPort)
- [ ] `GeminiFeignAdapter.java` (implements GeminiPort)
- [ ] `SlackNotificationAdapter.java` (implements AiNotificationPort)

### Phase 4: Application Services
- [ ] `AnalyzeService.java` (implements AnalyzeUseCase)
- [ ] `GetAiAnalysisService.java` (implements GetAiAnalysisUseCase)
- [ ] `GetAiAnalysisListService.java` (implements GetAiAnalysisListUseCase)
- [ ] `DeleteAiAnalysisService.java` (implements DeleteAiAnalysisUseCase)

### Phase 5: Presentation
- [ ] `AiController.java` — UseCase 주입으로 전환
- [ ] Command 생성 로직 (ChatMessage → String 변환)

### Phase 6: 레거시 삭제
- [ ] `domain/repository/` 전체 삭제
- [ ] `application/service/AiService.java` 삭제
- [ ] `domain/model/AI.java` (구버전) → 신규 순수 Java로 교체

### Phase 7: 테스트
- [ ] `application-test.yml` 생성
- [ ] `AiApplicationTests` contextLoads 통과
- [ ] `AnalyzeServiceTest` 작성
- [ ] `GetAiAnalysisServiceTest` 작성

---

## 13. 설계 결정 사항 (ADR)

| 결정 | 내용 | 이유 |
|------|------|------|
| GeminiServiceAdapter 유지 | GeminiFeignAdapter가 위임 | Feign 설정 재사용, 어댑터는 예외 변환만 담당 |
| SlackSender 유지 | SlackNotificationAdapter가 위임 | WebClient 설정 재사용 |
| PromptGenerator List\<String\> 전환 | ChatMessage DTO 의존성 제거 | 도메인 순수성 확보 |
| AiRepositoryPort.save() → AiResult 반환 | Adapter에서 JPA 저장 후 변환 | createdAt 등 Auditing 필드를 Result에 포함 |
| delete 소프트 삭제 유지 | `ai.markAsDeleted()` + `aiRepositoryPort.save()` | 기존 로직 유지 |
