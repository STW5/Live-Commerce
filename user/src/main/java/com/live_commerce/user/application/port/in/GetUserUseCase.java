package com.live_commerce.user.application.port.in;

import java.util.UUID;

import com.live_commerce.user.application.dto.result.UserGetResult;

public interface GetUserUseCase {
	UserGetResult getUser(UUID userId, UUID requesterId, boolean requesterIsMaster);
}
