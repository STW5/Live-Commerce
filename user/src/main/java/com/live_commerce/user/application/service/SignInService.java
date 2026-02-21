package com.live_commerce.user.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.user.application.dto.command.SignInCommand;
import com.live_commerce.user.application.dto.result.SignInResult;
import com.live_commerce.user.application.exception.CustomException;
import com.live_commerce.user.application.exception.UserExceptionCode;
import com.live_commerce.user.application.port.in.SignInUseCase;
import com.live_commerce.user.application.port.out.GenerateJwtPort;
import com.live_commerce.user.application.port.out.LoadUserPort;
import com.live_commerce.user.application.port.out.ManageUserTokenPort;
import com.live_commerce.user.domain.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class SignInService implements SignInUseCase {

	private final LoadUserPort loadUserPort;
	private final ManageUserTokenPort manageUserTokenPort;
	private final GenerateJwtPort generateJwtPort;
	private final PasswordEncoder passwordEncoder;

	@Value("${service.jwt.refresh-expiration}")
	private long refreshTokenExpirationMillis;

	@Override
	public SignInResult signIn(SignInCommand command) {
		User user = loadUserPort.loadByUsername(command.username())
			.filter(u -> passwordEncoder.matches(command.rawPassword(), u.getPassword()))
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
