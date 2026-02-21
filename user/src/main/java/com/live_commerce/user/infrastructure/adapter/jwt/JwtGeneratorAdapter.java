package com.live_commerce.user.infrastructure.adapter.jwt;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.live_commerce.user.application.port.out.GenerateJwtPort;
import com.live_commerce.user.domain.model.UserRole;
import com.live_commerce.user.infrastructure.common.JwtUtil;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtGeneratorAdapter implements GenerateJwtPort {

	private final JwtUtil jwtUtil;

	@Override
	public String createAccessToken(UUID userId, String username, UserRole userRole) {
		return jwtUtil.createAccessToken(userId, username, userRole);
	}

	@Override
	public String createRefreshToken(UUID userId) {
		return jwtUtil.createRefreshToken(userId);
	}

	@Override
	public void validateToken(String token) {
		jwtUtil.validateToken(token);
	}

	@Override
	public UUID extractUserId(String token) {
		Claims claims = jwtUtil.parseClaims(token);
		return UUID.fromString(claims.get("userId", String.class));
	}
}
