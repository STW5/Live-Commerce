package com.live_commerce.user.application.service;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.user.application.exception.CustomException;
import com.live_commerce.user.application.exception.UserExceptionCode;
import com.live_commerce.user.application.port.in.FindUsernameUseCase;
import com.live_commerce.user.application.port.out.LoadUserPort;
import com.live_commerce.user.application.port.out.ManageUserTokenPort;
import com.live_commerce.user.application.port.out.SendMailPort;
import com.live_commerce.user.domain.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class FindUsernameService implements FindUsernameUseCase {

	private static final long VERIFICATION_CODE_TTL_SECONDS = 300L;

	private final LoadUserPort loadUserPort;
	private final ManageUserTokenPort manageUserTokenPort;
	private final SendMailPort sendMailPort;

	@Override
	public void sendVerificationCode(String email) {
		loadUserPort.loadByEmail(email)
			.filter(u -> !u.isDeleted())
			.orElseThrow(() -> new CustomException(UserExceptionCode.USER_NOT_FOUND));

		String code = generateCode();
		manageUserTokenPort.storeVerificationCode(email, code, VERIFICATION_CODE_TTL_SECONDS);
		sendMailPort.sendVerificationCode(email, code);
	}

	@Override
	public String verifyCodeAndGetUsername(String email, String code) {
		String storedCode = manageUserTokenPort.getVerificationCode(email)
			.orElseThrow(() -> new CustomException(UserExceptionCode.VERIFICATION_CODE_EXPIRED));

		if (!storedCode.equals(code)) {
			throw new CustomException(UserExceptionCode.INVALID_VERIFICATION_CODE);
		}

		User user = loadUserPort.loadByEmail(email)
			.filter(u -> !u.isDeleted())
			.orElseThrow(() -> new CustomException(UserExceptionCode.USER_NOT_FOUND));

		manageUserTokenPort.deleteVerificationCode(email);
		return user.getUsername();
	}

	private String generateCode() {
		return String.valueOf(ThreadLocalRandom.current().nextInt(100_000, 1_000_000));
	}
}
