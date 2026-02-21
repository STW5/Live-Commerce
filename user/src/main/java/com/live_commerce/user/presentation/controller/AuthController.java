package com.live_commerce.user.presentation.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.live_commerce.user.application.dto.auth.request.TokenReissueRequestDto;
import com.live_commerce.user.application.dto.auth.request.UserFindUsernameRequestDto;
import com.live_commerce.user.application.dto.auth.request.UserFindUsernameVerifyRequestDto;
import com.live_commerce.user.application.dto.auth.request.UserResetPasswordRequestDto;
import com.live_commerce.user.application.dto.auth.request.UserSignInRequestDto;
import com.live_commerce.user.application.dto.auth.request.UserSignUpRequestDto;
import com.live_commerce.user.application.dto.auth.response.TokenReissueResponseDto;
import com.live_commerce.user.application.dto.auth.response.UserSignInResponseDto;
import com.live_commerce.user.application.dto.auth.response.UserSignUpResponseDto;
import com.live_commerce.user.application.service.AuthService;
import com.live_commerce.user.infrastructure.common.ResponseUtil;
import com.live_commerce.user.infrastructure.security.RequestUserDetails;
import com.live_commerce.user.presentation.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * @deprecated Use {@code AuthControllerV3} at /api/v3/auth instead.
 */
@Deprecated(since = "hexagonal-ddd-user", forRemoval = true)
@RestController
@RequestMapping("/api/v2/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@PostMapping("/signup")
	public ResponseEntity<ApiResponse<UserSignUpResponseDto>> signUp(
		@RequestBody @Valid UserSignUpRequestDto requestDto
	) {
		UserSignUpResponseDto response = authService.signUp(requestDto);
		return ResponseUtil.success(response);
	}

	@PostMapping("/signin")
	public ResponseEntity<ApiResponse<UserSignInResponseDto>> signIn(@RequestBody UserSignInRequestDto requestDto) {
		UserSignInResponseDto response = authService.signIn(requestDto);
		return ResponseUtil.success(response);
	}

	@PostMapping("/code")
	public ResponseEntity<ApiResponse<String>> sendFindUsernameCode(
		@RequestBody @Valid UserFindUsernameRequestDto request
	) {
		authService.sendFindUsernameCode(request.email());
		return ResponseUtil.success("인증번호가 이메일로 전송되었습니다.");
	}

	@PostMapping("/verify")
	public ResponseEntity<ApiResponse<String>> confirmFindUsernameCode(
		@RequestBody @Valid UserFindUsernameVerifyRequestDto request
	) {
		String username = authService.confirmFindUsernameCode(request.email(), request.code());
		return ResponseUtil.success(username);
	}

	@PostMapping("/reset-password")
	public ResponseEntity<ApiResponse<String>> resetPasswordAndSendTempPassword(
		@RequestBody @Valid UserResetPasswordRequestDto request
	) {
		authService.resetPasswordAndSendTempPassword(request.username(), request.email());
		return ResponseUtil.success("임시 비밀번호가 이메일로 전송되었습니다.");
	}

	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<String>> logout(
		@AuthenticationPrincipal RequestUserDetails userDetails
	) {
		authService.logout(userDetails.getUserId());
		return ResponseUtil.success("로그아웃 되었습니다.");
	}

	@PostMapping("/reissue")
	public ResponseEntity<ApiResponse<TokenReissueResponseDto>> reissue(
		@RequestBody @Valid TokenReissueRequestDto request
	) {
		TokenReissueResponseDto response = authService.reissueToken(request.refreshToken());
		return ResponseUtil.success(response);
	}

	@PostMapping("/approve/{userId}")
	@PreAuthorize("hasRole('MASTER')")
	public ResponseEntity<ApiResponse<String>> approveUser(@PathVariable UUID userId) {
		authService.approveUser(userId);
		return ResponseUtil.success("사용자가 승인되었습니다.");
	}

}
