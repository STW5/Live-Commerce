# Design: User 서비스 헥사고날 아키텍처 + DDD 적용

## 개요

- **Feature**: hexagonal-ddd-user
- **대상 서비스**: `user/`
- **작성일**: 2026-02-14
- **참조 Plan**: `docs/01-plan/features/hexagonal-ddd-domain-services.plan.md`
- **참조 패턴**: Payment (94.1%), Coupon (92.1%), Order pilot (91.4%)

---

## 현재 상태 분석 (As-Is)

User 서비스는 Port/Adapter 구조가 **전혀 없는** 레거시 상태이다.
Payment·Coupon과 달리 UseCase 인터페이스조차 존재하지 않으며 JPA가 도메인 전반에 오염되어 있다.

### 남은 문제점 (To-Fix)

| 파일 | 문제 | 심각도 |
|------|------|--------|
| `domain/model/User.java` | `@Entity`, `@Table`, `@Column`, `@Id`, JPA annotations | High |
| `domain/model/User.java` | `BaseEntity` (JPA) 상속 | High |
| `domain/model/BaseEntity.java` | `@MappedSuperclass`, `@EntityListeners` JPA annotations | High |
| `domain/repository/UserRepository.java` | `JpaRepository<User, UUID>` extends (도메인에 JPA 의존) | High |
| `domain/repository/UserQueryRepository.java` | QueryDSL 인터페이스, 도메인 레이어에 위치 | High |
| `domain/repository/UserQueryRepositoryImpl.java` | `QUser` 사용, 도메인 레이어에 위치 | High |
| `application/service/AuthService.java` | `UserRepository` JPA 직접 주입 | Medium |
| `application/service/AuthServiceV2.java` | `UserRepository` JPA 직접 주입, `JwtUtil`/`RedisUtil` 직접 주입 | Medium |
| `application/service/UserService.java` | `UserRepository` JPA 직접 주입 | Medium |
| `application/service/MailService.java` | `JavaMailSender` 직접 주입 (인프라 의존) | Medium |
| `presentation/controller/AuthController.java` | 레거시 `AuthService` 사용 (v2 엔드포인트) | Medium |
| `presentation/controller/UserController.java` | 레거시 `UserService` 사용 (v1 엔드포인트) | Medium |

### 특이사항 (Payment/Coupon 대비)

| 항목 | Payment | User |
|------|---------|------|
| 기존 UseCase 인터페이스 | 4개 존재 (부분 완료) | **없음** (0개) |
| 기존 Port 인터페이스 | 5개 존재 | **없음** (0개) |
| 기존 Adapter | 4개 존재 | **없음** (0개) |
| 레거시 서비스 수 | 2개 (PaymentService, V2) | **3개** (AuthService, AuthServiceV2, UserService) |
| Infrastructure 의존 다양성 | KakaoPay, Kafka, Redis | JWT, Redis, Kafka, JavaMail |

---

## 목표 패키지 구조 (To-Be)

```
user/src/main/java/com/live_commerce/user/
├── domain/
│   ├── model/
│   │   ├── User.java              # 순수 Java (JPA 없음)
│   │   └── UserRole.java          # 그대로 유지
│   └── event/
│       └── UserRegisteredEvent.java  # [선택] 도메인 이벤트
├── application/
│   ├── port/
│   │   ├── in/                    # UseCase 인터페이스 (신규)
│   │   │   ├── SignUpUseCase.java
│   │   │   ├── SignInUseCase.java
│   │   │   ├── LogoutUseCase.java
│   │   │   ├── ReissueTokenUseCase.java
│   │   │   ├── FindUsernameUseCase.java
│   │   │   ├── ResetPasswordUseCase.java
│   │   │   ├── ApproveUserUseCase.java
│   │   │   ├── GetUserUseCase.java
│   │   │   ├── SearchUserUseCase.java
│   │   │   ├── UpdateUserUseCase.java
│   │   │   └── DeleteUserUseCase.java
│   │   └── out/                   # Outbound Port 인터페이스 (신규)
│   │       ├── LoadUserPort.java
│   │       ├── SaveUserPort.java
│   │       ├── SearchUserPort.java
│   │       ├── PublishUserEventPort.java
│   │       ├── ManageUserTokenPort.java
│   │       ├── SendMailPort.java
│   │       └── GenerateJwtPort.java
│   ├── service/                   # 신규 헥사고날 서비스
│   │   ├── SignUpService.java
│   │   ├── SignInService.java
│   │   ├── LogoutService.java
│   │   ├── ReissueTokenService.java
│   │   ├── FindUsernameService.java
│   │   ├── ResetPasswordService.java
│   │   ├── ApproveUserService.java
│   │   ├── GetUserService.java
│   │   ├── SearchUserService.java
│   │   ├── UpdateUserService.java
│   │   └── DeleteUserService.java
│   └── dto/
│       ├── command/               # UseCase 입력 (신규)
│       │   ├── SignUpCommand.java
│       │   ├── SignInCommand.java
│       │   ├── LogoutCommand.java
│       │   ├── ReissueTokenCommand.java
│       │   ├── FindUsernameCommand.java
│       │   ├── VerifyUsernameCommand.java
│       │   ├── ResetPasswordCommand.java
│       │   ├── ApproveUserCommand.java
│       │   ├── GetUserCommand.java
│       │   ├── SearchUserCommand.java
│       │   ├── UpdateUserCommand.java
│       │   └── DeleteUserCommand.java
│       └── result/                # UseCase 출력 (신규)
│           ├── SignUpResult.java
│           ├── SignInResult.java
│           ├── TokenReissueResult.java
│           ├── UserGetResult.java
│           ├── UserUpdateResult.java
│           └── UserSearchResult.java
├── infrastructure/
│   └── adapter/
│       ├── persistence/           # JPA Adapter (신규)
│       │   ├── BaseJpaEntity.java
│       │   ├── UserJpaEntity.java
│       │   ├── UserJpaRepository.java
│       │   ├── UserQueryJpaRepository.java
│       │   ├── UserQueryJpaRepositoryImpl.java
│       │   └── UserPersistenceAdapter.java
│       ├── event/                 # Kafka Adapter (신규)
│       │   └── KafkaUserEventPublisherAdapter.java
│       ├── token/                 # Redis Adapter (신규)
│       │   └── RedisUserTokenAdapter.java
│       ├── mail/                  # Mail Adapter (신규)
│       │   └── JavaMailNotificationAdapter.java
│       └── jwt/                   # JWT Adapter (신규)
│           └── JwtGeneratorAdapter.java
└── presentation/
    └── controller/
        ├── AuthControllerV3.java  # 신규 (v3)
        └── UserControllerV3.java  # 신규 (v3)
```

---

## 단계별 구현 계획

### Step 1: User 도메인 모델 JPA 분리

**대상 파일**: `domain/model/User.java`, `domain/model/BaseEntity.java`

**User.java 변경사항**:
- `@Entity`, `@Table`, `@Column`, `@Id`, `@UuidGenerator` 제거
- `extends BaseEntity` 제거
- `User.reconstitute()` 정적 팩토리 추가 (DB 재구성용, ID 포함)
- `softDelete(String deletedBy)` 도메인 메서드 추가 (BaseEntity의 `markAsDeleted` 대체)
- `isDeleted()` 도메인 메서드 추가 (BaseEntity의 `isDeletedStatus` 대체)
- 삭제 상태 필드 도메인에 직접 포함 (`deletedStatus`, `deletedAt`, `deletedBy`)

```java
// AS-IS
@Entity
@Table(name = "p_user")
public class User extends BaseEntity {
    @Id @UuidGenerator
    private UUID userId;
    @Column private String username;
    ...
}

// TO-BE
public class User {
    private UUID userId;          // null for new object, populated after save
    private String username;
    private String password;
    private String email;
    private String nickname;
    private boolean alarmConsent;
    private UserRole userRole;
    private boolean approved;
    private boolean deletedStatus; // BaseEntity에서 도메인으로 이동
    private String deletedBy;

    // 신규 객체용 (ID 없음)
    public static User of(String username, String password, String email,
                          String nickname, boolean alarmConsent,
                          UserRole userRole, boolean approved) { ... }

    // DB 재구성용 (ID 있음)
    public static User reconstitute(UUID userId, String username, String password,
                                    String email, String nickname, boolean alarmConsent,
                                    UserRole userRole, boolean approved,
                                    boolean deletedStatus, String deletedBy) { ... }

    // 기존 도메인 메서드 유지
    public void updateUser(...)
    public void changePassword(String newEncodedPassword)
    public boolean isApproved()
    public void approve()

    // BaseEntity 대체 메서드 (신규)
    public boolean isDeleted() { return this.deletedStatus; }
    public void softDelete(String deletedBy) {
        this.deletedStatus = true;
        this.deletedBy = deletedBy;
    }
}
```

**BaseEntity.java**:
- `@Deprecated(since = "hexagonal-ddd-user", forRemoval = true)` 추가

---

### Step 2: BaseJpaEntity + UserJpaEntity 생성

**위치**: `infrastructure/adapter/persistence/`

**BaseJpaEntity.java** (신규):
```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseJpaEntity {
    @CreatedDate @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
    @Column(nullable = false)
    private boolean deletedStatus = false;
    private LocalDateTime deletedAt;
    @CreatedBy @Column(updatable = false)
    private String createdBy;
    @LastModifiedBy
    private String updatedBy;
    private String deletedBy;
    // markAsDeleted() 메서드 포함
}
```

**UserJpaEntity.java** (신규):
```java
@Entity
@Table(name = "p_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserJpaEntity extends BaseJpaEntity {
    @Id @UuidGenerator
    private UUID userId;
    @Column(nullable = false, unique = true)
    private String username;
    @Column(nullable = false)
    private String password;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private String nickname;
    @Column(nullable = false)
    private boolean alarmConsent;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole userRole;
    @Column(nullable = false)
    private boolean approved;

    // Domain → JPA
    public static UserJpaEntity from(User domain) { ... }

    // JPA → Domain
    public User toDomain() {
        return User.reconstitute(userId, username, password, email, nickname,
                                 alarmConsent, userRole, approved,
                                 isDeletedStatus(), getDeletedBy());
    }
}
```

---

### Step 3: Repository Migration (도메인 → 어댑터)

**삭제 대상**:
- `domain/repository/UserRepository.java` — 삭제
- `domain/repository/UserQueryRepository.java` — 삭제
- `domain/repository/UserQueryRepositoryImpl.java` — 삭제 (QUser 사용 중)

**신규 생성** (`infrastructure/adapter/persistence/`):

**UserJpaRepository.java**:
```java
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID>,
        UserQueryJpaRepository {
    boolean existsByEmail(String email);
    Optional<UserJpaEntity> findByEmail(String email);
    Optional<UserJpaEntity> findByUsernameAndEmail(String username, String email);
    Optional<UserJpaEntity> findByUsername(String username);
}
```

**UserQueryJpaRepository.java** + **UserQueryJpaRepositoryImpl.java**:
- `QUser` → `QUserJpaEntity` 로 교체
- 동일한 검색 조건 유지 (username, email, nickname, userRole, alarmConsent)

---

### Step 4: Outbound Port 인터페이스 정의

**위치**: `application/port/out/`

#### LoadUserPort.java
```java
public interface LoadUserPort {
    Optional<User> loadById(UUID userId);
    Optional<User> loadByUsername(String username);
    Optional<User> loadByEmail(String email);
    Optional<User> loadByUsernameAndEmail(String username, String email);
    boolean existsByEmail(String email);
}
```

#### SaveUserPort.java
```java
public interface SaveUserPort {
    User save(User user);  // 반환값 필수 — DB 생성 UUID 포함
}
```

#### SearchUserPort.java
```java
public interface SearchUserPort {
    List<User> searchUser(UserSearchCondition condition);
}
```

#### PublishUserEventPort.java
```java
public interface PublishUserEventPort {
    void publishFirstJoinCouponEvent(UUID userId);
}
```

#### ManageUserTokenPort.java
```java
public interface ManageUserTokenPort {
    void storeRefreshToken(UUID userId, String refreshToken, long expirationMillis);
    Optional<String> getRefreshToken(UUID userId);
    void deleteRefreshToken(UUID userId);
    void storeVerificationCode(String email, String code, long expirationSeconds);
    Optional<String> getVerificationCode(String email);
    void deleteVerificationCode(String email);
}
```

#### SendMailPort.java
```java
public interface SendMailPort {
    void sendVerificationCode(String email, String code);
    void sendTemporaryPassword(String email, String tempPassword);
}
```

#### GenerateJwtPort.java
```java
public interface GenerateJwtPort {
    String createAccessToken(UUID userId, String username, UserRole userRole);
    String createRefreshToken(UUID userId);
    void validateToken(String token);
    UUID extractUserId(String token);
    String extractUsername(String token);
    UserRole extractUserRole(String token);
}
```

---

### Step 5: UserPersistenceAdapter

`LoadUserPort`, `SaveUserPort`, `SearchUserPort` 구현체.

**패키지**: `infrastructure/adapter/persistence/UserPersistenceAdapter.java`

```java
@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements LoadUserPort, SaveUserPort, SearchUserPort {

    private final UserJpaRepository userJpaRepository;

    @Override
    public User save(User user) {
        return userJpaRepository.save(UserJpaEntity.from(user)).toDomain();
    }

    @Override
    public Optional<User> loadById(UUID userId) {
        return userJpaRepository.findById(userId).map(UserJpaEntity::toDomain);
    }

    @Override
    public Optional<User> loadByUsername(String username) {
        return userJpaRepository.findByUsername(username).map(UserJpaEntity::toDomain);
    }

    @Override
    public Optional<User> loadByEmail(String email) {
        return userJpaRepository.findByEmail(email).map(UserJpaEntity::toDomain);
    }

    @Override
    public Optional<User> loadByUsernameAndEmail(String username, String email) {
        return userJpaRepository.findByUsernameAndEmail(username, email).map(UserJpaEntity::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmail(email);
    }

    @Override
    public List<User> searchUser(UserSearchCondition condition) {
        return userJpaRepository.searchUser(condition)
            .stream().map(UserJpaEntity::toDomain).toList();
    }
}
```

**핵심**: `save()` 반환값 반드시 캡처할 것 — `UserJpaEntity.save()` 후 DB 생성 UUID가 포함된 도메인 객체 반환.

---

### Step 6: 기타 Outbound Adapters

#### KafkaUserEventPublisherAdapter.java
```java
@Component
@RequiredArgsConstructor
public class KafkaUserEventPublisherAdapter implements PublishUserEventPort {
    private final KafkaTemplate<String, FirstJoinCouponEvent> kafkaTemplate;
    private static final String FIRST_COUPON_TOPIC = "first-join-coupon";

    @Override
    public void publishFirstJoinCouponEvent(UUID userId) {
        kafkaTemplate.send(FIRST_COUPON_TOPIC, userId.toString(),
                           new FirstJoinCouponEvent(userId));
    }
}
```

#### RedisUserTokenAdapter.java
```java
@Component
@RequiredArgsConstructor
public class RedisUserTokenAdapter implements ManageUserTokenPort {
    private final RedisUtil redisUtil;
    private static final String REFRESH_KEY_PREFIX = "RT:";

    @Override
    public void storeRefreshToken(UUID userId, String refreshToken, long expirationMillis) {
        redisUtil.setDataExpire(REFRESH_KEY_PREFIX + userId, refreshToken, expirationMillis);
    }
    @Override
    public Optional<String> getRefreshToken(UUID userId) {
        return Optional.ofNullable(redisUtil.getData(REFRESH_KEY_PREFIX + userId));
    }
    @Override
    public void deleteRefreshToken(UUID userId) {
        redisUtil.deleteData(REFRESH_KEY_PREFIX + userId);
    }
    @Override
    public void storeVerificationCode(String email, String code, long expirationSeconds) {
        redisUtil.setDataExpire(email, code, expirationSeconds);
    }
    @Override
    public Optional<String> getVerificationCode(String email) {
        return Optional.ofNullable(redisUtil.getData(email));
    }
    @Override
    public void deleteVerificationCode(String email) {
        redisUtil.deleteData(email);
    }
}
```

#### JavaMailNotificationAdapter.java
```java
@Component
@RequiredArgsConstructor
public class JavaMailNotificationAdapter implements SendMailPort {
    private final JavaMailSender mailSender;

    @Override
    public void sendVerificationCode(String email, String code) { ... }
    @Override
    public void sendTemporaryPassword(String email, String tempPassword) { ... }
}
```

#### JwtGeneratorAdapter.java
```java
@Component
@RequiredArgsConstructor
public class JwtGeneratorAdapter implements GenerateJwtPort {
    private final JwtUtil jwtUtil;

    @Override
    public String createAccessToken(UUID userId, String username, UserRole userRole) {
        return jwtUtil.createAccessToken(userId, username, userRole);
    }
    @Override
    public String createRefreshToken(UUID userId) {
        return jwtUtil.createRefreshToken(userId);
    }
    @Override
    public void validateToken(String token) {
        jwtUtil.validateToken(token);
    }
    @Override
    public UUID extractUserId(String token) {
        return UUID.fromString(jwtUtil.parseClaims(token).get("userId", String.class));
    }
    // ...
}
```

---

### Step 7: Application Services (신규 헥사고날)

각 UseCase 인터페이스 + 구현 서비스.

#### Command Records

**SignUpCommand.java**:
```java
public record SignUpCommand(
    String username, String password, String email,
    String nickname, boolean alarmConsent,
    UserRole userRole, String masterKey  // null if non-MASTER
) {}
```

**SignInCommand.java**:
```java
public record SignInCommand(String username, String password) {}
```

**ReissueTokenCommand.java**:
```java
public record ReissueTokenCommand(String refreshToken) {}
```

**UpdateUserCommand.java**:
```java
public record UpdateUserCommand(
    UUID targetUserId,
    String password, String email, String nickname,
    Boolean alarmConsent, UserRole userRole,
    UUID requesterId, String requesterRole
) {}
```

**GetUserCommand / DeleteUserCommand / ApproveUserCommand**: 동일 패턴.

#### SignUpService.java (핵심 예시)

```java
@Service
@RequiredArgsConstructor
@Transactional
public class SignUpService implements SignUpUseCase {

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final PublishUserEventPort publishUserEventPort;

    @Value("${user.master-key}")
    private String masterKey;

    @Override
    public SignUpResult signUp(SignUpCommand command) {
        // 1. 이메일 중복 검증
        if (loadUserPort.existsByEmail(command.email())) {
            throw new CustomException(UserExceptionCode.DUPLICATE_EMAIL);
        }
        // 2. MASTER 권한 키 검증
        if (command.userRole() == UserRole.MASTER) {
            if (!masterKey.equals(command.masterKey())) {
                throw new CustomException(UserExceptionCode.INVALID_MASTER_KEY);
            }
        }
        // 3. 승인 여부 결정
        boolean approved = switch (command.userRole()) {
            case SELLER, SHOW_HOST -> false;
            default -> true;
        };
        // 4. 도메인 객체 생성 + 저장 (반환값 캡처 필수)
        User user = User.of(command.username(), command.password(),
                            command.email(), command.nickname(),
                            command.alarmConsent(), command.userRole(), approved);
        User savedUser = saveUserPort.save(user);

        // 5. 첫가입 쿠폰 이벤트 발행 (Kafka)
        publishUserEventPort.publishFirstJoinCouponEvent(savedUser.getUserId());

        return SignUpResult.from(savedUser);
    }
}
```

> **주의**: `saveUserPort.save(user)` 반환값 반드시 캡처. `user.getUserId()`는 save 이전에 null.

#### SignInService.java (핵심 예시)

```java
@Service
@RequiredArgsConstructor
@Transactional
public class SignInService implements SignInUseCase {

    private final LoadUserPort loadUserPort;
    private final ManageUserTokenPort manageUserTokenPort;
    private final GenerateJwtPort generateJwtPort;

    @Value("${service.jwt.refresh-expiration}")
    private long refreshTokenExpirationMillis;

    @Override
    public SignInResult signIn(SignInCommand command) {
        User user = loadUserPort.loadByUsername(command.username())
            .filter(u -> passwordMatches(command.password(), u.getPassword()))
            .filter(u -> !u.isDeleted())
            .orElseThrow(() -> new CustomException(UserExceptionCode.INVALID_CREDENTIALS));

        if (!user.isApproved()) {
            throw new CustomException(UserExceptionCode.UNAPPROVED_USER);
        }

        String accessToken = generateJwtPort.createAccessToken(
            user.getUserId(), user.getUsername(), user.getUserRole());
        String refreshToken = generateJwtPort.createRefreshToken(user.getUserId());

        manageUserTokenPort.storeRefreshToken(
            user.getUserId(), refreshToken, refreshTokenExpirationMillis);

        return new SignInResult(accessToken, refreshToken);
    }
}
```

#### FindUsernameService.java (검증 코드 2단계 흐름)

```java
@Service
@RequiredArgsConstructor
@Transactional
public class FindUsernameService implements FindUsernameUseCase {

    private final LoadUserPort loadUserPort;
    private final ManageUserTokenPort manageUserTokenPort;
    private final SendMailPort sendMailPort;

    @Override
    public void sendVerificationCode(FindUsernameCommand command) {
        loadUserPort.loadByEmail(command.email())
            .filter(u -> !u.isDeleted())
            .orElseThrow(() -> new CustomException(UserExceptionCode.USER_NOT_FOUND));
        String code = generateCode();
        manageUserTokenPort.storeVerificationCode(command.email(), code, 300);
        sendMailPort.sendVerificationCode(command.email(), code);
    }

    @Override
    public String verifyCodeAndGetUsername(VerifyUsernameCommand command) {
        String storedCode = manageUserTokenPort.getVerificationCode(command.email())
            .orElseThrow(() -> new CustomException(UserExceptionCode.VERIFICATION_CODE_EXPIRED));
        if (!storedCode.equals(command.code())) {
            throw new CustomException(UserExceptionCode.INVALID_VERIFICATION_CODE);
        }
        User user = loadUserPort.loadByEmail(command.email())
            .filter(u -> !u.isDeleted())
            .orElseThrow(() -> new CustomException(UserExceptionCode.USER_NOT_FOUND));
        manageUserTokenPort.deleteVerificationCode(command.email());
        return user.getUsername();
    }
}
```

---

### Step 8: AuthControllerV3 + UserControllerV3

**AuthControllerV3.java** (`/api/v3/auth/`):

```java
@RestController
@RequestMapping("/api/v3/auth")
@RequiredArgsConstructor
public class AuthControllerV3 {
    private final SignUpUseCase signUpUseCase;
    private final SignInUseCase signInUseCase;
    private final LogoutUseCase logoutUseCase;
    private final ReissueTokenUseCase reissueTokenUseCase;
    private final FindUsernameUseCase findUsernameUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final ApproveUserUseCase approveUserUseCase;

    @PostMapping("/signup")        // POST /api/v3/auth/signup
    @PostMapping("/signin")        // POST /api/v3/auth/signin
    @PostMapping("/logout")        // POST /api/v3/auth/logout
    @PostMapping("/reissue")       // POST /api/v3/auth/reissue
    @PostMapping("/code")          // POST /api/v3/auth/code
    @PostMapping("/verify")        // POST /api/v3/auth/verify
    @PostMapping("/reset-password")// POST /api/v3/auth/reset-password
    @PostMapping("/approve/{userId}") // POST /api/v3/auth/approve/{userId} [MASTER only]
}
```

**UserControllerV3.java** (`/api/v3/users/`):

```java
@RestController
@RequestMapping("/api/v3/users")
@RequiredArgsConstructor
public class UserControllerV3 {
    private final GetUserUseCase getUserUseCase;
    private final SearchUserUseCase searchUserUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final DeleteUserUseCase deleteUserUseCase;

    @GetMapping("/{userId}")       // GET /api/v3/users/{userId}
    @GetMapping("/search")         // GET /api/v3/users/search [MASTER]
    @PutMapping("/{userId}")       // PUT /api/v3/users/{userId}
    @DeleteMapping("/{userId}")    // DELETE /api/v3/users/{userId} [MASTER]
}
```

---

### Step 9: 레거시 @Deprecated 처리

| 파일 | 처리 방법 |
|------|----------|
| `application/service/AuthService.java` | `@Deprecated(since="hexagonal-ddd-user", forRemoval=true)` + Javadoc: `{@code SignUpService}`, `{@code SignInService}` 참조 |
| `application/service/AuthServiceV2.java` | `@Deprecated(since="hexagonal-ddd-user", forRemoval=true)` |
| `application/service/UserService.java` | `@Deprecated(since="hexagonal-ddd-user", forRemoval=true)` |
| `application/service/MailService.java` | `@Deprecated(since="hexagonal-ddd-user", forRemoval=true)` + `{@code JavaMailNotificationAdapter}` 참조 |
| `presentation/controller/AuthController.java` | `@Deprecated(since="hexagonal-ddd-user", forRemoval=true)` |
| `presentation/controller/UserController.java` | `@Deprecated(since="hexagonal-ddd-user", forRemoval=true)` |
| `domain/model/BaseEntity.java` | `@Deprecated(since="hexagonal-ddd-user", forRemoval=true)` |

---

## 검증 기준 (Verification Criteria)

| 검증 항목 | 기대값 | 비고 |
|----------|--------|------|
| `User.java`에 `import jakarta.persistence.*` 없음 | 없음 | 도메인 순수성 |
| `domain/repository/` 디렉토리 삭제 | 삭제됨 | 또는 @Deprecated |
| `application/port/in/` UseCase 인터페이스 11개 | 존재 | |
| `application/port/out/` Port 인터페이스 7개 | 존재 | |
| `/api/v3/auth/` 엔드포인트 | 존재 | AuthControllerV3 |
| `/api/v3/users/` 엔드포인트 | 존재 | UserControllerV3 |
| `saveUserPort.save()` 반환값 캡처 | `User savedUser =` | SignUpService 필수 |
| 레거시 서비스 4개 `@Deprecated` | 존재 | AuthService, V2, UserService, MailService |
| 빌드 성공 | 성공 | |

---

## Payment 대비 User 특이점

| 항목 | Payment | User |
|------|---------|------|
| UseCase 수 | 5개 | **11개** |
| Outbound Port 수 | 5개 | **7개** |
| 신규 Adapter 수 | 4개 | **5개** (JWT 추가) |
| MailService 처리 | 해당없음 | `application.service.MailService` → `JavaMailNotificationAdapter` 이동 |
| JWT 처리 | 해당없음 | `JwtUtil` → `GenerateJwtPort` + `JwtGeneratorAdapter` 추상화 |
| Redis 역할 | 결제 만료 타이머 | 리프레시 토큰 + 인증코드 TTL 관리 |
| 도메인 soft-delete | `BaseEntity.markAsDeleted()` → JpaEntity만 | `User.softDelete()` 도메인 메서드로 이동 |

---

## 구현 완료 기준

- [ ] Step 1: `User.java` JPA annotations 제거, `reconstitute()` 추가
- [ ] Step 2: `UserJpaEntity.java`, `BaseJpaEntity.java` 생성
- [ ] Step 3: `UserJpaRepository` + QueryDSL 어댑터 계층으로 이동
- [ ] Step 4: Port 인터페이스 7개 생성 (`application/port/out/`)
- [ ] Step 4: UseCase 인터페이스 11개 생성 (`application/port/in/`)
- [ ] Step 5: `UserPersistenceAdapter` 구현
- [ ] Step 6: 4개 아웃바운드 어댑터 구현 (Kafka, Redis, Mail, JWT)
- [ ] Step 7: Command/Result records + 11개 서비스 구현
- [ ] Step 8: `AuthControllerV3`, `UserControllerV3` 생성
- [ ] Step 9: 레거시 7개 파일 `@Deprecated` 처리
- [ ] 빌드 성공 확인

---

## 버전 이력

| 버전 | 날짜 | 변경 내용 | 작성자 |
|------|------|---------|--------|
| 1.0 | 2026-02-14 | 최초 작성 | frontend-architect |
