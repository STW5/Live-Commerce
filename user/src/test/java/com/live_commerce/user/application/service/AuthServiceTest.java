package com.live_commerce.user.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.live_commerce.user.application.dto.auth.request.*;
import com.live_commerce.user.application.dto.auth.response.*;
import com.live_commerce.user.application.exception.*;
import com.live_commerce.user.domain.model.*;
import com.live_commerce.user.infrastructure.adapter.persistence.UserJpaEntity;
import com.live_commerce.user.infrastructure.adapter.persistence.UserJpaRepository;
import com.live_commerce.user.infrastructure.client.CouponClient;
import com.live_commerce.user.infrastructure.common.JwtUtil;
import com.live_commerce.user.infrastructure.common.RedisUtil;
import org.springframework.mail.javamail.JavaMailSender;

import io.jsonwebtoken.Claims;

@SpringBootTest(properties = "spring.config.import=optional:configserver:")
@ActiveProfiles("test")
@Transactional
class AuthServiceTest {

	@Autowired private AuthService authService;

	@MockitoBean
	private UserJpaRepository userJpaRepository;
	@MockitoBean
	private PasswordEncoder passwordEncoder;
	@MockitoBean
	private JwtUtil jwtUtil;
	@MockitoBean
	private RedisUtil redisUtil;
	@MockitoBean
	private MailService mailService;
	@MockitoBean
	private CouponClient couponClient;
	@MockitoBean
	private JavaMailSender javaMailSender;

	@DisplayName("회원가입 성공")
	@Test
	void signUp_success() {
		// Given
		UserSignUpRequestDto req = new UserSignUpRequestDto("testuser", "password", "test@email.com", "nickname", true, UserRole.CUSTOMER, null);
		when(userJpaRepository.existsByEmail(req.email())).thenReturn(false);
		when(passwordEncoder.encode(req.password())).thenReturn("encoded");
		when(userJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		// When
		UserSignUpResponseDto res = authService.signUp(req);

		// Then
		assertThat(res).isNotNull();
		verify(couponClient).issueFirstCoupon(any());
	}

	@DisplayName("회원가입 실패 - 마스터키 불일치")
	@Test
	void signUp_masterKey_invalid() {
		// Given
		UserSignUpRequestDto req = new UserSignUpRequestDto("admin", "pass", "admin@email.com", "nickname", true, UserRole.MASTER, "wrong-key");
		when(userJpaRepository.existsByEmail(any())).thenReturn(false);

		// When & Then
		assertThatThrownBy(() -> authService.signUp(req))
			.isInstanceOf(CustomException.class)
			.hasMessage(UserExceptionCode.INVALID_MASTER_KEY.getMessage());
	}

	@DisplayName("로그인 성공")
	@Test
	void signIn_success() {
		// Given - approved=true로 승인된 유저
		User user = User.of("testuser", "encoded", "email@test.com", "nickname", true, UserRole.CUSTOMER, true);
		when(userJpaRepository.findByUsername("testuser")).thenReturn(Optional.of(UserJpaEntity.from(user)));
		when(passwordEncoder.matches("password", "encoded")).thenReturn(true);
		when(jwtUtil.createAccessToken(any(), any(), any())).thenReturn("access-token");
		when(jwtUtil.createRefreshToken(any())).thenReturn("refresh-token");

		UserSignInRequestDto req = new UserSignInRequestDto("testuser", "password");

		// When
		UserSignInResponseDto res = authService.signIn(req);

		// Then
		assertThat(res.accessToken()).isEqualTo("access-token");
		assertThat(res.refreshToken()).isEqualTo("refresh-token");
		verify(redisUtil).setDataExpire(startsWith("RT:"), eq("refresh-token"), anyLong());
	}

	@Test
	@DisplayName("로그인 실패 - 탈퇴 유저")
	void signIn_deletedUser() {
		// Given
		User user = User.of("user", "encoded", "email", "nick", true, UserRole.CUSTOMER, true);
		user.softDelete("admin");

		when(userJpaRepository.findByUsername("user")).thenReturn(Optional.of(UserJpaEntity.from(user)));
		when(passwordEncoder.matches("password", "encoded")).thenReturn(true);

		// When & Then
		CustomException ex = catchThrowableOfType(
			() -> authService.signIn(new UserSignInRequestDto("user", "password")),
			CustomException.class
		);

		assertThat(ex.getExceptionCode()).isEqualTo(UserExceptionCode.DELETED_USER);
	}

	@DisplayName("토큰 재발급 성공")
	@Test
	void reissueToken_success() {
		// Given
		UUID userId = UUID.randomUUID();
		Claims claims = mock(Claims.class);
		when(claims.get("userId", String.class)).thenReturn(userId.toString());
		when(jwtUtil.parseClaims("refresh-token")).thenReturn(claims);
		when(redisUtil.getData("RT:" + userId)).thenReturn("refresh-token");

		User user = User.reconstitute(userId, "testuser", "pw", "email", "nickname", true, UserRole.CUSTOMER, true, false, null);
		when(userJpaRepository.findById(userId)).thenReturn(Optional.of(UserJpaEntity.from(user)));
		when(jwtUtil.createAccessToken(any(), any(), any())).thenReturn("new-access");
		when(jwtUtil.createRefreshToken(any())).thenReturn("new-refresh");

		// When
		TokenReissueResponseDto res = authService.reissueToken("refresh-token");

		// Then
		assertThat(res.accessToken()).isEqualTo("new-access");
		assertThat(res.refreshToken()).isEqualTo("new-refresh");
	}

	@DisplayName("로그아웃 성공")
	@Test
	void logout_success() {
		// Given
		UUID userId = UUID.randomUUID();

		// When
		authService.logout(userId);

		// Then
		verify(redisUtil).deleteData("RT:" + userId);
	}

	@Test
	@DisplayName("아이디 찾기 성공")
	void findUsername_success() {
		// Given
		String email = "user@email.com";
		String expectedUsername = "testuser";
		String inputCode = "123456";

		User user = User.of(expectedUsername, "pw", email, "nick", true, UserRole.CUSTOMER, false);

		when(userJpaRepository.findByEmail(email)).thenReturn(Optional.of(UserJpaEntity.from(user)));
		when(redisUtil.getData(email)).thenReturn(inputCode);

		// When
		String username = authService.confirmFindUsernameCode(email, inputCode);

		// Then
		assertThat(username).isEqualTo(expectedUsername);
		verify(redisUtil).deleteData(email);
	}

	@Test
	@DisplayName("아이디 찾기 실패 - 인증코드 만료")
	void findUsername_codeExpired() {
		// Given
		String email = "user@email.com";
		when(redisUtil.getData(email)).thenReturn(null); // 없으면 만료

		// When & Then
		CustomException ex = catchThrowableOfType(() ->
			authService.confirmFindUsernameCode(email, "123456"), CustomException.class);

		assertThat(ex.getExceptionCode()).isEqualTo(UserExceptionCode.VERIFICATION_CODE_EXPIRED);
	}

	@Test
	@DisplayName("아이디 찾기 실패 - 인증코드 불일치")
	void findUsername_invalidCode() {
		// Given
		String email = "user@email.com";
		when(redisUtil.getData(email)).thenReturn("actual-code");

		// When & Then
		CustomException ex = catchThrowableOfType(() ->
			authService.confirmFindUsernameCode(email, "wrong-code"), CustomException.class);

		assertThat(ex.getExceptionCode()).isEqualTo(UserExceptionCode.INVALID_VERIFICATION_CODE);
	}

	@Test
	@DisplayName("아이디 찾기 실패 - 유저 없음")
	void findUsername_userNotFound() {
		// Given
		String email = "user@email.com";
		String inputCode = "123456";

		when(redisUtil.getData(email)).thenReturn(inputCode);
		when(userJpaRepository.findByEmail(email)).thenReturn(Optional.empty());

		// When & Then
		CustomException ex = catchThrowableOfType(() ->
			authService.confirmFindUsernameCode(email, inputCode), CustomException.class);

		assertThat(ex.getExceptionCode()).isEqualTo(UserExceptionCode.USER_NOT_FOUND);
	}

	@Test
	@DisplayName("아이디 찾기 실패 - 탈퇴 유저")
	void findUsername_deletedUser() {
		// given
		String email = "user@email.com";
		String inputCode = "123456";

		User user = User.of("deletedUser", "pw", email, "nick", true, UserRole.CUSTOMER, true);
		user.softDelete("test");

		when(userJpaRepository.findByEmail(email)).thenReturn(Optional.of(UserJpaEntity.from(user)));
		when(redisUtil.getData(email)).thenReturn(inputCode);

		// when & then
		CustomException ex = catchThrowableOfType(
			() -> authService.confirmFindUsernameCode(email, inputCode), CustomException.class);

		assertThat(ex.getExceptionCode()).isEqualTo(UserExceptionCode.DELETED_USER);
	}

	@DisplayName("임시 비밀번호 전송 성공")
	@Test
	void resetPasswordAndSendTempPassword_success() {
		// Given
		User user = User.of("user", "pw", "email", "nickname", true, UserRole.CUSTOMER, false);
		when(userJpaRepository.findByUsernameAndEmail("user", "email")).thenReturn(Optional.of(UserJpaEntity.from(user)));
		when(passwordEncoder.encode(any())).thenReturn("encoded");

		// When
		authService.resetPasswordAndSendTempPassword("user", "email");

		// Then
		verify(userJpaRepository).findByUsernameAndEmail("user", "email");
		verify(mailService).sendTemporaryPassword(eq("email"), any());
	}

	@Test
	@DisplayName("임시 비밀번호 전송 실패 - 탈퇴 유저")
	void resetPassword_deletedUser() {
		// Given
		User deletedUser = User.of("user", "pw", "email", "nickname", true, UserRole.CUSTOMER, true);
		deletedUser.softDelete("admin");

		when(userJpaRepository.findByUsernameAndEmail("user", "email"))
			.thenReturn(Optional.of(UserJpaEntity.from(deletedUser)));

		// When & Then
		CustomException ex = catchThrowableOfType(
			() -> authService.resetPasswordAndSendTempPassword("user", "email"),
			CustomException.class
		);

		assertThat(ex.getExceptionCode()).isEqualTo(UserExceptionCode.DELETED_USER);
	}

}
