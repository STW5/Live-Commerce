package com.live_commerce.user.application.dto.result;

import java.util.UUID;

import com.live_commerce.user.domain.model.User;
import com.live_commerce.user.domain.model.UserRole;

public record UserGetResult(
	UUID userId,
	String username,
	String email,
	String nickname,
	boolean alarmConsent,
	UserRole userRole,
	boolean approved
) {
	public static UserGetResult from(User user) {
		return new UserGetResult(
			user.getUserId(), user.getUsername(), user.getEmail(),
			user.getNickname(), user.isAlarmConsent(), user.getUserRole(), user.isApproved()
		);
	}
}
