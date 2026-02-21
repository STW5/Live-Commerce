package com.live_commerce.user.application.dto.result;

import java.util.UUID;

import com.live_commerce.user.domain.model.User;
import com.live_commerce.user.domain.model.UserRole;

public record SignUpResult(
	UUID userId,
	String username,
	String email,
	String nickname,
	UserRole userRole,
	boolean approved
) {
	public static SignUpResult from(User user) {
		return new SignUpResult(
			user.getUserId(), user.getUsername(), user.getEmail(),
			user.getNickname(), user.getUserRole(), user.isApproved()
		);
	}
}
