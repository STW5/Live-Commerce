package com.live_commerce.user.application.port.in;

public interface ResetPasswordUseCase {
	void resetPasswordAndSendTempPassword(String username, String email);
}
