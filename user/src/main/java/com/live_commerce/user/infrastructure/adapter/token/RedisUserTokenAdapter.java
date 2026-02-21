package com.live_commerce.user.infrastructure.adapter.token;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.live_commerce.user.application.port.out.ManageUserTokenPort;
import com.live_commerce.user.infrastructure.common.RedisUtil;

import lombok.RequiredArgsConstructor;

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
