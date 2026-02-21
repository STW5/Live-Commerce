package com.live_commerce.user.application.dto.command;

import java.util.UUID;

import com.live_commerce.user.domain.model.UserRole;

public record UpdateUserCommand(
	UUID targetUserId,
	String encodedPassword,   // null if not changing
	String email,             // null if not changing
	String nickname,          // null if not changing
	Boolean alarmConsent,     // null if not changing
	UserRole userRole,        // null if not changing
	UUID requesterId,
	boolean requesterIsMaster
) {}
