package com.live_commerce.user.application.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.user.application.dto.command.ReissueTokenCommand;
import com.live_commerce.user.application.dto.result.TokenReissueResult;
import com.live_commerce.user.application.exception.CustomException;
import com.live_commerce.user.application.exception.UserExceptionCode;
import com.live_commerce.user.application.port.in.ReissueTokenUseCase;
import com.live_commerce.user.application.port.out.GenerateJwtPort;
import com.live_commerce.user.application.port.out.LoadUserPort;
import com.live_commerce.user.application.port.out.ManageUserTokenPort;
import com.live_commerce.user.domain.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ReissueTokenService implements ReissueTokenUseCase {

	private final LoadUserPort loadUserPort;
	private final ManageUserTokenPort manageUserTokenPort;
	private final GenerateJwtPort generateJwtPort;

	@Value("${service.jwt.refresh-expiration}")
	private long refreshTokenExpirationMillis;

	@Override
	public TokenReissueResult reissue(ReissueTokenCommand command) {
		generateJwtPort.validateToken(command.refreshToken());
		UUID userId = generateJwtPort.extractUserId(command.refreshToken());

		String storedToken = manageUserTokenPort.getRefreshToken(userId)
			.orElseThrow(() -> new CustomException(UserExceptionCode.INVALID_REFRESH_TOKEN));

		if (!storedToken.equals(command.refreshToken())) {
			throw new CustomException(UserExceptionCode.INVALID_REFRESH_TOKEN);
		}

		User user = loadUserPort.loadById(userId)
			.filter(u -> !u.isDeleted())
			.orElseThrow(() -> new CustomException(UserExceptionCode.USER_NOT_FOUND));

		String newAccessToken = generateJwtPort.createAccessToken(
			userId, user.getUsername(), user.getUserRole());
		String newRefreshToken = generateJwtPort.createRefreshToken(userId);

		manageUserTokenPort.storeRefreshToken(userId, newRefreshToken, refreshTokenExpirationMillis);
		return new TokenReissueResult(newAccessToken, newRefreshToken);
	}
}
