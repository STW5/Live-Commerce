package com.live_commerce.user.application.dto.command;

import com.live_commerce.user.domain.model.UserRole;

public record SignUpCommand(
	String username,
	String encodedPassword,
	String email,
	String nickname,
	boolean alarmConsent,
	UserRole userRole,
	boolean approved
) {}
