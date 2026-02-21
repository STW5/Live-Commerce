package com.live_commerce.user.application.port.out;

public interface SendMailPort {
	void sendVerificationCode(String email, String code);
	void sendTemporaryPassword(String email, String tempPassword);
}
