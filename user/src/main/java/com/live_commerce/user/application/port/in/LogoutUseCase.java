package com.live_commerce.user.application.port.in;

import java.util.UUID;

public interface LogoutUseCase {
	void logout(UUID userId);
}
