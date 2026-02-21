package com.live_commerce.user.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.user.application.dto.auth.request.UserSearchCondition;
import com.live_commerce.user.application.dto.auth.request.UserUpdateRequestDto;
import com.live_commerce.user.application.dto.auth.response.UserUpdateResponseDto;
import com.live_commerce.user.application.dto.user.response.UserGetResponseDto;
import com.live_commerce.user.application.exception.CustomException;
import com.live_commerce.user.application.exception.UserExceptionCode;
import com.live_commerce.user.domain.model.User;
import com.live_commerce.user.infrastructure.adapter.persistence.UserJpaEntity;
import com.live_commerce.user.infrastructure.adapter.persistence.UserJpaRepository;
import com.live_commerce.user.infrastructure.security.RequestUserDetails;

import lombok.RequiredArgsConstructor;

/**
 * @deprecated Use {@code GetUserService}, {@code SearchUserService},
 *             {@code UpdateUserService}, {@code DeleteUserService} instead.
 */
@Deprecated(since = "hexagonal-ddd-user", forRemoval = true)
@Service
@RequiredArgsConstructor
public class UserService {

	private final UserJpaRepository userJpaRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional(readOnly = true)
	public UserGetResponseDto getUser(UUID userId, RequestUserDetails userDetails) {
		validateUserGetPermission(userId, userDetails);

		User user;
		if (hasMasterRole(userDetails)) {
			user = findUserEvenIfDeleted(userId); // 마스터는 삭제 유저도 조회 가능
		} else {
			user = findUserById(userId); // 일반 유저는 삭제 유저 조회 시 예외
		}

		return UserGetResponseDto.from(user);
	}

	@Transactional(readOnly = true)
	public Page<UserGetResponseDto> searchUser(UserSearchCondition condition, Pageable pageable, RequestUserDetails userDetails) {
		validateUserSearchPermission(userDetails);

		int size = pageable.getPageSize();
		if (size != 10 && size != 30 && size != 50) {
			pageable = PageRequest.of(pageable.getPageNumber(), 10, pageable.getSort());
		}

		List<User> users = userJpaRepository.searchUser(condition).stream()
			.map(UserJpaEntity::toDomain)
			.toList();
		List<UserGetResponseDto> dtoList = users.stream()
			.map(UserGetResponseDto::from)
			.toList();

		return new PageImpl<>(dtoList, pageable, dtoList.size());
	}

	@Transactional
	public UserUpdateResponseDto updateUser(UUID userId, UserUpdateRequestDto requestDto, RequestUserDetails userDetails) {
		validateUserUpdatePermission(userId, requestDto, userDetails);

		User user;
		if (hasMasterRole(userDetails)) {
			user = findUserEvenIfDeleted(userId); // 마스터는 삭제 유저도 수정 가능
		} else {
			user = findUserById(userId); // 일반 유저는 삭제 유저 수정 시 예외
		}

		String updatedPassword = requestDto.password() != null
			? passwordEncoder.encode(requestDto.password())
			: user.getPassword();

		user.updateUser(
			updatedPassword,
			requestDto.email() != null ? requestDto.email() : user.getEmail(),
			requestDto.nickname() != null ? requestDto.nickname() : user.getNickname(),
			requestDto.alarmConsent() != null ? requestDto.alarmConsent() : user.isAlarmConsent(),
			requestDto.userRole() != null ? requestDto.userRole() : user.getUserRole()
		);

		User savedUser = userJpaRepository.save(UserJpaEntity.from(user)).toDomain();
		return UserUpdateResponseDto.from(savedUser);
	}

	@Transactional
	public void deleteUser(UUID userId, RequestUserDetails userDetails) {
		validateUserDeletePermission(userId, userDetails);
		User user = findUserById(userId);
		user.softDelete(userDetails.getUsername());
		userJpaRepository.save(UserJpaEntity.from(user));
	}

	private void validateUserGetPermission(UUID userId, RequestUserDetails userDetails) {
		if (!isSelf(userId, userDetails) && !hasMasterRole(userDetails)) {
			throw new CustomException(UserExceptionCode.FORBIDDEN);
		}
	}

	private void validateUserSearchPermission(RequestUserDetails userDetails) {
		if (!hasMasterRole(userDetails)) {
			throw new CustomException(UserExceptionCode.FORBIDDEN);
		}
	}

	private void validateUserUpdatePermission(UUID userId, UserUpdateRequestDto dto, RequestUserDetails userDetails) {
		if (!isSelf(userId, userDetails) && !hasMasterRole(userDetails)) {
			throw new CustomException(UserExceptionCode.FORBIDDEN);
		}
		if (!hasMasterRole(userDetails) && dto.userRole() != null) {
			throw new CustomException(UserExceptionCode.ROLE_CHANGE_FORBIDDEN);
		}
	}

	private void validateUserDeletePermission(UUID userId, RequestUserDetails userDetails) {
		if (!isSelf(userId, userDetails) && !hasMasterRole(userDetails)) {
			throw new CustomException(UserExceptionCode.FORBIDDEN);
		}
	}

	private User findUserById(UUID userId) {
		User user = userJpaRepository.findById(userId)
			.map(UserJpaEntity::toDomain)
			.orElseThrow(() -> new CustomException(UserExceptionCode.USER_NOT_FOUND));

		if (user.isDeleted()) {
			throw new CustomException(UserExceptionCode.DELETED_USER);
		}
		return user;
	}

	private User findUserEvenIfDeleted(UUID userId) {
		return userJpaRepository.findById(userId)
			.map(UserJpaEntity::toDomain)
			.orElseThrow(() -> new CustomException(UserExceptionCode.USER_NOT_FOUND));
	}

	private boolean hasMasterRole(RequestUserDetails userDetails) {
		return userDetails.getAuthorities().stream()
			.anyMatch(auth -> auth.getAuthority().equals("ROLE_MASTER"));
	}

	private boolean isSelf(UUID userId, RequestUserDetails userDetails) {
		return userId.equals(userDetails.getUserId());
	}
}
