package com.live_commerce.user.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.user.application.dto.auth.request.UserUpdateRequestDto;
import com.live_commerce.user.application.dto.auth.response.UserUpdateResponseDto;
import com.live_commerce.user.application.dto.user.response.UserGetResponseDto;
import com.live_commerce.user.application.exception.CustomException;
import com.live_commerce.user.application.exception.UserExceptionCode;
import com.live_commerce.user.domain.model.User;
import com.live_commerce.user.domain.model.UserRole;
import com.live_commerce.user.infrastructure.adapter.persistence.UserJpaEntity;
import com.live_commerce.user.infrastructure.adapter.persistence.UserJpaRepository;
import com.live_commerce.user.infrastructure.client.CouponClient;
import com.live_commerce.user.infrastructure.common.JwtUtil;
import com.live_commerce.user.infrastructure.common.RedisUtil;
import com.live_commerce.user.infrastructure.security.RequestUserDetails;
import org.springframework.mail.javamail.JavaMailSender;

@SpringBootTest(properties = "spring.config.import=optional:configserver:")
@ActiveProfiles("test")
@Transactional
class UserServiceTest {

	@MockitoBean private UserJpaRepository userJpaRepository;
	@MockitoBean private PasswordEncoder passwordEncoder;

	@MockitoBean private JwtUtil jwtUtil;
	@MockitoBean private RedisUtil redisUtil;
	@MockitoBean private MailService mailService;
	@MockitoBean private CouponClient couponClient;
	@MockitoBean private JavaMailSender javaMailSender;

	@Autowired private UserService userService;

	private final UUID userId = UUID.randomUUID();


	@Test
	@DisplayName("자기 자신 조회 성공")
	void getUser_self_success() {
		// given
		User user = createUser();
		RequestUserDetails self = createUserDetails(userId, "ROLE_CUSTOMER");
		when(userJpaRepository.findById(userId)).thenReturn(Optional.of(UserJpaEntity.from(user)));

		// when
		UserGetResponseDto result = userService.getUser(userId, self);

		// then
		assertThat(result.getUsername()).isEqualTo("user");
	}

	@Test
	@DisplayName("마스터가 타인 조회 성공")
	void getUser_master_success() {
		// given
		User user = createUser();
		RequestUserDetails master = createUserDetails(UUID.randomUUID(), "ROLE_MASTER");
		when(userJpaRepository.findById(userId)).thenReturn(Optional.of(UserJpaEntity.from(user)));

		// when - then
		assertThat(userService.getUser(userId, master).getEmail()).isEqualTo("email@test.com");
	}

	@Test
	@DisplayName("권한 없는 유저가 타인 조회 → FORBIDDEN")
	void getUser_forbidden() {
		// given
		RequestUserDetails other = createUserDetails(UUID.randomUUID(), "ROLE_CUSTOMER");

		// expect
		CustomException ex = catchThrowableOfType(
			() -> userService.getUser(userId, other), CustomException.class);

		assertThat(ex.getExceptionCode()).isEqualTo(UserExceptionCode.FORBIDDEN);
	}

	@Test
	@DisplayName("조회 실패 - 존재하지 않는 유저")
	void getUser_notFound() {
		// given
		RequestUserDetails self = createUserDetails(userId, "ROLE_CUSTOMER");
		when(userJpaRepository.findById(userId)).thenReturn(Optional.empty());

		// expect
		CustomException ex = catchThrowableOfType(() -> userService.getUser(userId, self), CustomException.class);

		assertThat(ex.getExceptionCode()).isEqualTo(UserExceptionCode.USER_NOT_FOUND);
	}

	@Test
	@DisplayName("검색 실패 - 마스터가 아닌 유저")
	void searchUser_forbidden() {
		// given
		RequestUserDetails normal = createUserDetails(UUID.randomUUID(), "ROLE_CUSTOMER");

		// expect
		CustomException ex = catchThrowableOfType(
			() -> userService.searchUser(null, PageRequest.of(0, 10), normal),
			CustomException.class
		);

		assertThat(ex.getExceptionCode()).isEqualTo(UserExceptionCode.FORBIDDEN);
	}

	@Test
	@DisplayName("유저 정보 수정 성공")
	void updateUser_success() {
		// given
		User user = createUser();
		RequestUserDetails self = createUserDetails(userId, "ROLE_CUSTOMER");
		UserUpdateRequestDto dto =
			new UserUpdateRequestDto("newPw", "new@mail.com", "newnick", true, null);

		when(userJpaRepository.findById(userId)).thenReturn(Optional.of(UserJpaEntity.from(user)));
		when(passwordEncoder.encode("newPw")).thenReturn("encodedPw");
		when(userJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		// when
		UserUpdateResponseDto res = userService.updateUser(userId, dto, self);

		// then
		assertThat(res.email()).isEqualTo("new@mail.com");
		assertThat(res.nickname()).isEqualTo("newnick");
	}

	@Test
	@DisplayName("마스터가 삭제된 유저 정보 수정 성공")
	void updateUser_master_canUpdateDeletedUser() {
		// given
		User deletedUser = createUser();
		deletedUser.softDelete("admin");
		RequestUserDetails master = createUserDetails(UUID.randomUUID(), "ROLE_MASTER");

		UserUpdateRequestDto dto = new UserUpdateRequestDto("pw", "new@email.com", "newNick", true, null);

		when(userJpaRepository.findById(userId)).thenReturn(Optional.of(UserJpaEntity.from(deletedUser)));
		when(passwordEncoder.encode("pw")).thenReturn("encoded");
		when(userJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		// when
		UserUpdateResponseDto result = userService.updateUser(userId, dto, master);

		// then
		assertThat(result.email()).isEqualTo("new@email.com");
		assertThat(result.nickname()).isEqualTo("newNick");
	}

	@Test
	@DisplayName("마스터가 삭제된 유저 조회 성공")
	void getUser_master_canViewDeletedUser() {
		// given
		User deletedUser = createUser();
		deletedUser.softDelete("admin");
		RequestUserDetails master = createUserDetails(UUID.randomUUID(), "ROLE_MASTER");

		when(userJpaRepository.findById(userId)).thenReturn(Optional.of(UserJpaEntity.from(deletedUser)));

		// when
		UserGetResponseDto result = userService.getUser(userId, master);

		// then
		assertThat(result.getUsername()).isEqualTo("user");
	}

	@Test
	@DisplayName("일반 유저가 삭제된 유저 조회 시 예외")
	void getUser_deletedUser_forbidden() {
		// given
		User deletedUser = createUser();
		deletedUser.softDelete("admin");
		RequestUserDetails self = createUserDetails(userId, "ROLE_CUSTOMER");

		when(userJpaRepository.findById(userId)).thenReturn(Optional.of(UserJpaEntity.from(deletedUser)));

		// expect
		CustomException ex = catchThrowableOfType(() ->
			userService.getUser(userId, self), CustomException.class);

		assertThat(ex.getExceptionCode()).isEqualTo(UserExceptionCode.DELETED_USER);
	}

	@Test
	@DisplayName("수정 실패 - 삭제된 유저")
	void updateUser_deletedUser() {
		// given
		User user = createUser();
		user.softDelete("test");
		UserUpdateRequestDto dto =
			new UserUpdateRequestDto("pw", "email", "nick", true, null);
		RequestUserDetails self = createUserDetails(userId, "ROLE_CUSTOMER");

		when(userJpaRepository.findById(userId)).thenReturn(Optional.of(UserJpaEntity.from(user)));

		// expect
		CustomException ex = catchThrowableOfType(() ->
			userService.updateUser(userId, dto, self), CustomException.class);

		assertThat(ex.getExceptionCode()).isEqualTo(UserExceptionCode.DELETED_USER);
	}

	@Test
	@DisplayName("마스터 아닌 유저가 권한 변경 → ROLE_CHANGE_FORBIDDEN")
	void updateUser_roleChangeForbidden() {
		// given
		RequestUserDetails normal = createUserDetails(userId, "ROLE_CUSTOMER");
		User user = createUser();
		UserUpdateRequestDto dto =
			new UserUpdateRequestDto(null, null, null, null, UserRole.MASTER);

		when(userJpaRepository.findById(userId)).thenReturn(Optional.of(UserJpaEntity.from(user)));

		// expect
		CustomException ex = catchThrowableOfType(
			() -> userService.updateUser(userId, dto, normal), CustomException.class);

		assertThat(ex.getExceptionCode()).isEqualTo(UserExceptionCode.ROLE_CHANGE_FORBIDDEN);
	}

	@Test
	@DisplayName("마스터가 유저 삭제 성공")
	void deleteUser_success() {
		// given
		User user = createUser();
		RequestUserDetails master = createUserDetails(UUID.randomUUID(), "ROLE_MASTER");
		when(userJpaRepository.findById(userId)).thenReturn(Optional.of(UserJpaEntity.from(user)));

		// when
		userService.deleteUser(userId, master);

		// then - 저장된 엔티티의 deletedStatus 확인
		ArgumentCaptor<UserJpaEntity> captor = ArgumentCaptor.forClass(UserJpaEntity.class);
		verify(userJpaRepository).save(captor.capture());
		assertThat(captor.getValue().toDomain().isDeleted()).isTrue();
	}

	@DisplayName("삭제 성공 - 일반 유저가 본인 계정 삭제")
	@Test
	void deleteUser_self_success() {
		// Given
		User user = createUser();
		RequestUserDetails self = createUserDetails(userId, "ROLE_CUSTOMER");

		when(userJpaRepository.findById(userId)).thenReturn(Optional.of(UserJpaEntity.from(user)));

		// When
		userService.deleteUser(userId, self);

		// Then - 저장된 엔티티의 deletedStatus 확인
		ArgumentCaptor<UserJpaEntity> captor = ArgumentCaptor.forClass(UserJpaEntity.class);
		verify(userJpaRepository).save(captor.capture());
		assertThat(captor.getValue().toDomain().isDeleted()).isTrue();
	}

	@Test
	@DisplayName("삭제 실패 - 일반 유저가 타인 삭제 시도")
	void deleteUser_others_forbidden() {
		// Given
		RequestUserDetails other = createUserDetails(UUID.randomUUID(), "ROLE_CUSTOMER");

		// When & Then
		CustomException ex = catchThrowableOfType(
			() -> userService.deleteUser(userId, other), CustomException.class);

		assertThat(ex.getExceptionCode()).isEqualTo(UserExceptionCode.FORBIDDEN);
	}


	private User createUser() {
		return User.reconstitute(userId, "user", "pw", "email@test.com",
			"nickname", true, UserRole.CUSTOMER, true, false, null);
	}

	private RequestUserDetails createUserDetails(UUID id, String role) {
		return new RequestUserDetails(id, "user", List.of(() -> role));
	}
}
