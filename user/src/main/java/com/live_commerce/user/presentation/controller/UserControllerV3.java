package com.live_commerce.user.presentation.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.live_commerce.user.application.dto.auth.request.UserSearchCondition;
import com.live_commerce.user.application.dto.auth.request.UserUpdateRequestDto;
import com.live_commerce.user.application.dto.command.UpdateUserCommand;
import com.live_commerce.user.application.dto.result.UserGetResult;
import com.live_commerce.user.application.dto.result.UserUpdateResult;
import com.live_commerce.user.application.port.in.DeleteUserUseCase;
import com.live_commerce.user.application.port.in.GetUserUseCase;
import com.live_commerce.user.application.port.in.SearchUserUseCase;
import com.live_commerce.user.application.port.in.UpdateUserUseCase;
import com.live_commerce.user.infrastructure.common.ResponseUtil;
import com.live_commerce.user.infrastructure.security.RequestUserDetails;
import com.live_commerce.user.presentation.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v3/users")
@RequiredArgsConstructor
public class UserControllerV3 {

	private final GetUserUseCase getUserUseCase;
	private final SearchUserUseCase searchUserUseCase;
	private final UpdateUserUseCase updateUserUseCase;
	private final DeleteUserUseCase deleteUserUseCase;
	private final PasswordEncoder passwordEncoder;

	@GetMapping("/{userId}")
	@PreAuthorize("#userId == authentication.principal.userId or hasRole('MASTER')")
	public ResponseEntity<ApiResponse<UserGetResult>> getUser(
		@PathVariable UUID userId,
		@AuthenticationPrincipal RequestUserDetails userDetails
	) {
		boolean isMaster = userDetails.getAuthorities().stream()
			.anyMatch(a -> a.getAuthority().equals("ROLE_MASTER"));
		return ResponseUtil.success(getUserUseCase.getUser(userId, userDetails.getUserId(), isMaster));
	}

	@GetMapping("/search")
	@PreAuthorize("hasRole('MASTER')")
	public ResponseEntity<ApiResponse<List<UserGetResult>>> searchUsers(
		@ModelAttribute UserSearchCondition condition,
		@AuthenticationPrincipal RequestUserDetails userDetails
	) {
		return ResponseUtil.success(searchUserUseCase.searchUser(condition));
	}

	@PutMapping("/{userId}")
	@PreAuthorize("#userId == authentication.principal.userId or hasRole('MASTER')")
	public ResponseEntity<ApiResponse<UserUpdateResult>> updateUser(
		@PathVariable UUID userId,
		@RequestBody @Valid UserUpdateRequestDto requestDto,
		@AuthenticationPrincipal RequestUserDetails userDetails
	) {
		boolean isMaster = userDetails.getAuthorities().stream()
			.anyMatch(a -> a.getAuthority().equals("ROLE_MASTER"));
		String encodedPassword = requestDto.password() != null
			? passwordEncoder.encode(requestDto.password())
			: null;
		UpdateUserCommand command = new UpdateUserCommand(
			userId, encodedPassword,
			requestDto.email(), requestDto.nickname(), requestDto.alarmConsent(),
			requestDto.userRole(), userDetails.getUserId(), isMaster
		);
		return ResponseUtil.success(updateUserUseCase.updateUser(command));
	}

	@DeleteMapping("/{userId}")
	@PreAuthorize("hasRole('MASTER')")
	public ResponseEntity<ApiResponse<Void>> deleteUser(
		@PathVariable UUID userId,
		@AuthenticationPrincipal RequestUserDetails userDetails
	) {
		boolean isMaster = userDetails.getAuthorities().stream()
			.anyMatch(a -> a.getAuthority().equals("ROLE_MASTER"));
		deleteUserUseCase.deleteUser(userId, userDetails.getUserId(), isMaster);
		return ResponseUtil.noContent();
	}
}
