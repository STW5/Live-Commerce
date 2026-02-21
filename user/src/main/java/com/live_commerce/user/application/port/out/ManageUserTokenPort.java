package com.live_commerce.user.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface ManageUserTokenPort {
	void storeRefreshToken(UUID userId, String refreshToken, long expirationMillis);
	Optional<String> getRefreshToken(UUID userId);
	void deleteRefreshToken(UUID userId);

	void storeVerificationCode(String email, String code, long expirationSeconds);
	Optional<String> getVerificationCode(String email);
	void deleteVerificationCode(String email);
}
