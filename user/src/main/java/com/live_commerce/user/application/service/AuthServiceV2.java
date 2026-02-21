package com.live_commerce.user.application.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.live_commerce.user.application.dto.auth.request.UserSignInRequestDto;
import com.live_commerce.user.application.dto.auth.request.UserSignUpRequestDto;
import com.live_commerce.user.application.dto.auth.response.UserSignInResponseDto;
import com.live_commerce.user.application.dto.auth.response.UserSignUpResponseDto;
import com.live_commerce.user.application.dto.auth.response.TokenReissueResponseDto;
import com.live_commerce.user.application.exception.CustomException;
import com.live_commerce.user.application.exception.UserExceptionCode;
import com.live_commerce.user.domain.model.User;
import com.live_commerce.user.domain.model.UserRole;
import com.live_commerce.user.infrastructure.adapter.persistence.UserJpaEntity;
import com.live_commerce.user.infrastructure.adapter.persistence.UserJpaRepository;
import com.live_commerce.user.infrastructure.common.JwtUtil;
import com.live_commerce.user.infrastructure.common.PasswordGenerator;
import com.live_commerce.user.infrastructure.common.RedisUtil;
import com.live_commerce.user.infrastructure.kafka.event.FirstJoinCouponEvent;
import com.live_commerce.user.infrastructure.kafka.producer.FirstJoinCouponProducer;

import io.jsonwebtoken.Claims;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

/**
 * @deprecated Use {@code SignUpService}, {@code SignInService}, {@code LogoutService},
 *             {@code ReissueTokenService}, {@code FindUsernameService},
 *             {@code ResetPasswordService}, {@code ApproveUserService} instead.
 *             Legacy service (Kafka-based coupon issuance).
 */
@Deprecated(since = "hexagonal-ddd-user", forRemoval = true)
@Service
@RequiredArgsConstructor
public class AuthServiceV2 {

	private final UserJpaRepository userJpaRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtUtil jwtUtil;
	private final RedisUtil redisUtil;
	private final MailService mailService;
	private final FirstJoinCouponProducer firstJoinCouponProducer;

	private static final String REFRESH_KEY_PREFIX = "RT:";

	@Value("${service.jwt.refresh-expiration}")
	private long refreshTokenExpirationMillis;

	@Value("${user.master-key}")
	private String masterKey;

	@Transactional
	public UserSignUpResponseDto signUp(UserSignUpRequestDto request) {
		validateEmail(request.email());

		// MASTER 권한 등록 키 검증
		if (request.userRole() == UserRole.MASTER) {
			validateMasterRegistrationKey(request.masterKey());
		}

		boolean approved = switch (request.userRole()) {
			case SELLER, SHOW_HOST -> false;
			default -> true;
		};

		String encodedPassword = passwordEncoder.encode(request.password());
		User user = request.toEntity(encodedPassword, approved);
		User savedUser = userJpaRepository.save(UserJpaEntity.from(user)).toDomain();

		// Kafka로 첫가입 쿠폰 발급 이벤트 발행
		firstJoinCouponProducer.send(new FirstJoinCouponEvent(savedUser.getUserId()));

		return UserSignUpResponseDto.from(savedUser);
	}

	@Transactional
	public UserSignInResponseDto signIn(UserSignInRequestDto requestDto) {
		User user = userJpaRepository.findByUsername(requestDto.username())
			.map(UserJpaEntity::toDomain)
			.filter(u -> passwordEncoder.matches(requestDto.password(), u.getPassword()))
			.map(this::validateActiveUser)
			.orElseThrow(() -> new CustomException(UserExceptionCode.INVALID_CREDENTIALS));

		if (!user.isApproved()) {
			throw new CustomException(UserExceptionCode.UNAPPROVED_USER);
		}

		UUID userId = user.getUserId();
		String accessToken = jwtUtil.createAccessToken(userId, user.getUsername(), user.getUserRole());
		String refreshToken = jwtUtil.createRefreshToken(userId);

		String redisKey = REFRESH_KEY_PREFIX + userId;
		redisUtil.setDataExpire(redisKey, refreshToken, refreshTokenExpirationMillis);

		return UserSignInResponseDto.from(accessToken, refreshToken);
	}

	@Transactional
	public TokenReissueResponseDto reissueToken(String refreshToken) {
		jwtUtil.validateToken(refreshToken);
		Claims token = jwtUtil.parseClaims(refreshToken);

		UUID userId = UUID.fromString(token.get("userId", String.class));
		String redisKey = REFRESH_KEY_PREFIX + userId;
		String storedRefreshToken = redisUtil.getData(redisKey);

		if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
			throw new CustomException(UserExceptionCode.INVALID_REFRESH_TOKEN);
		}

		User user = userJpaRepository.findById(userId)
			.map(UserJpaEntity::toDomain)
			.map(this::validateActiveUser)
			.orElseThrow(() -> new CustomException(UserExceptionCode.USER_NOT_FOUND));

		String newAccessToken = jwtUtil.createAccessToken(userId, user.getUsername(), user.getUserRole());
		String newRefreshToken = jwtUtil.createRefreshToken(userId);

		redisUtil.setDataExpire(redisKey, newRefreshToken, refreshTokenExpirationMillis);
		return new TokenReissueResponseDto(newAccessToken, newRefreshToken);
	}

	@Transactional
	public void logout(UUID userId) {
		String redisKey = REFRESH_KEY_PREFIX + userId;
		redisUtil.deleteData(redisKey);
	}

	@Transactional
	public void sendFindUsernameCode(String email) {
		findActiveUserByEmail(email);
		mailService.sendVerificationCode(email);
	}

	@Transactional
	public String confirmFindUsernameCode(String email, String inputCode) {
		String storedCode = redisUtil.getData(email);

		if (storedCode == null) {
			throw new CustomException(UserExceptionCode.VERIFICATION_CODE_EXPIRED);
		}

		if (!storedCode.equals(inputCode)) {
			throw new CustomException(UserExceptionCode.INVALID_VERIFICATION_CODE);
		}

		User user = findActiveUserByEmail(email);
		redisUtil.deleteData(email);
		return user.getUsername();
	}

	@Transactional
	public void resetPasswordAndSendTempPassword(String username, String email) {
		User user = findActiveUserByUsernameAndEmail(username, email);
		String tempPassword = PasswordGenerator.generateTempPassword(10);
		String encoded = passwordEncoder.encode(tempPassword);
		user.changePassword(encoded);
		userJpaRepository.save(UserJpaEntity.from(user));
		mailService.sendTemporaryPassword(email, tempPassword);
	}

	@Transactional
	public void approveUser(UUID userId) {
		User user = userJpaRepository.findById(userId)
			.map(UserJpaEntity::toDomain)
			.orElseThrow(() -> new CustomException(UserExceptionCode.USER_NOT_FOUND));

		user.approve();
		userJpaRepository.save(UserJpaEntity.from(user));
	}

	private User findActiveUserByEmail(String email) {
		return userJpaRepository.findByEmail(email)
			.map(UserJpaEntity::toDomain)
			.map(this::validateActiveUser)
			.orElseThrow(() -> new CustomException(UserExceptionCode.USER_NOT_FOUND));
	}

	private User findActiveUserByUsernameAndEmail(String username, String email) {
		return userJpaRepository.findByUsernameAndEmail(username, email)
			.map(UserJpaEntity::toDomain)
			.map(this::validateActiveUser)
			.orElseThrow(() -> new CustomException(UserExceptionCode.USER_NOT_FOUND));
	}

	private User validateActiveUser(User user) {
		if (user.isDeleted()) {
			throw new CustomException(UserExceptionCode.DELETED_USER);
		}
		return user;
	}

	private void validateEmail(String email) {
		validateDuplicate(userJpaRepository.existsByEmail(email), UserExceptionCode.DUPLICATE_EMAIL);
	}

	private void validateDuplicate(boolean exists, UserExceptionCode exceptionCode) {
		if (exists) {
			throw new CustomException(exceptionCode);
		}
	}

	private void validateMasterRegistrationKey(String inputKey) {
		if (!inputKey.equals(masterKey)) {
			throw new CustomException(UserExceptionCode.INVALID_MASTER_KEY);
		}
	}
}
