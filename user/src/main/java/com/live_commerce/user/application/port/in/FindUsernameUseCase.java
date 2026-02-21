package com.live_commerce.user.application.port.in;

public interface FindUsernameUseCase {
	void sendVerificationCode(String email);
	String verifyCodeAndGetUsername(String email, String code);
}
