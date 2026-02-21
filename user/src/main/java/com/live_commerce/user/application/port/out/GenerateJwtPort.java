package com.live_commerce.user.application.port.out;

import java.util.UUID;

import com.live_commerce.user.domain.model.UserRole;

public interface GenerateJwtPort {
	String createAccessToken(UUID userId, String username, UserRole userRole);
	String createRefreshToken(UUID userId);
	void validateToken(String token);
	UUID extractUserId(String token);
}
