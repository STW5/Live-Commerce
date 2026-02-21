package com.live_commerce.user.application.port.in;

import java.util.UUID;

public interface DeleteUserUseCase {
	void deleteUser(UUID userId, UUID requesterId, boolean requesterIsMaster);
}
