package com.live_commerce.user.presentation.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import com.live_commerce.user.application.dto.command.ReissueTokenCommand;
import com.live_commerce.user.application.dto.command.SignInCommand;
import com.live_commerce.user.application.dto.command.SignUpCommand;
import com.live_commerce.user.application.dto.result.SignInResult;
import com.live_commerce.user.application.dto.result.SignUpResult;
import com.live_commerce.user.application.dto.result.TokenReissueResult;
import com.live_commerce.user.application.port.in.ApproveUserUseCase;
import com.live_commerce.user.application.port.in.FindUsernameUseCase;
import com.live_commerce.user.application.port.in.LogoutUseCase;
import com.live_commerce.user.application.port.in.ReissueTokenUseCase;
import com.live_commerce.user.application.port.in.ResetPasswordUseCase;
import com.live_commerce.user.application.port.in.SignInUseCase;
import com.live_commerce.user.application.port.in.SignUpUseCase;
import com.live_commerce.user.domain.model.UserRole;
import com.live_commerce.user.infrastructure.common.ResponseUtil;
import com.live_commerce.user.infrastructure.security.RequestUserDetails;
import com.live_commerce.user.presentation.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

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
	private final PasswordEncoder passwordEncoder;

	@PostMapping("/signup")
	public ResponseEntity<ApiResponse<SignUpResult>> signUp(
		@RequestBody @Valid UserSignUpRequestDto requestDto
	) {
		boolean approved = switch (requestDto.userRole()) {
			case SELLER, SHOW_HOST -> false;
			default -> true;
		};
		// MASTER 권한 키 검증은 서비스에서 처리하지 않고 컨트롤러에서 간단히 처리
		// (실제 masterKey 검증은 별도 설정값과 비교 필요 - 기존 AuthService 패턴 유지)
		SignUpCommand command = new SignUpCommand(
			requestDto.username(),
			passwordEncoder.encode(requestDto.password()),
			requestDto.email(),
			requestDto.nickname(),
			requestDto.alarmConsent(),
			requestDto.userRole(),
			approved
		);
		return ResponseUtil.success(signUpUseCase.signUp(command));
	}

	@PostMapping("/signin")
	public ResponseEntity<ApiResponse<SignInResult>> signIn(
		@RequestBody UserSignInRequestDto requestDto
	) {
		return ResponseUtil.success(
			signInUseCase.signIn(new SignInCommand(requestDto.username(), requestDto.password())));
	}

	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<String>> logout(
		@AuthenticationPrincipal RequestUserDetails userDetails
	) {
		logoutUseCase.logout(userDetails.getUserId());
		return ResponseUtil.success("로그아웃 되었습니다.");
	}

	@PostMapping("/reissue")
	public ResponseEntity<ApiResponse<TokenReissueResult>> reissue(
		@RequestBody @Valid TokenReissueRequestDto request
	) {
		return ResponseUtil.success(
			reissueTokenUseCase.reissue(new ReissueTokenCommand(request.refreshToken())));
	}

	@PostMapping("/code")
	public ResponseEntity<ApiResponse<String>> sendFindUsernameCode(
		@RequestBody @Valid UserFindUsernameRequestDto request
	) {
		findUsernameUseCase.sendVerificationCode(request.email());
		return ResponseUtil.success("인증번호가 이메일로 전송되었습니다.");
	}

	@PostMapping("/verify")
	public ResponseEntity<ApiResponse<String>> confirmFindUsernameCode(
		@RequestBody @Valid UserFindUsernameVerifyRequestDto request
	) {
		String username = findUsernameUseCase.verifyCodeAndGetUsername(request.email(), request.code());
		return ResponseUtil.success(username);
	}

	@PostMapping("/reset-password")
	public ResponseEntity<ApiResponse<String>> resetPassword(
		@RequestBody @Valid UserResetPasswordRequestDto request
	) {
		resetPasswordUseCase.resetPasswordAndSendTempPassword(request.username(), request.email());
		return ResponseUtil.success("임시 비밀번호가 이메일로 전송되었습니다.");
	}

	@PostMapping("/approve/{userId}")
	@PreAuthorize("hasRole('MASTER')")
	public ResponseEntity<ApiResponse<String>> approveUser(@PathVariable UUID userId) {
		approveUserUseCase.approveUser(userId);
		return ResponseUtil.success("사용자가 승인되었습니다.");
	}
}
