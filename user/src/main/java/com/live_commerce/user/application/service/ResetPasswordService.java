package com.live_commerce.user.application.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.user.application.exception.CustomException;
import com.live_commerce.user.application.exception.UserExceptionCode;
import com.live_commerce.user.application.port.in.ResetPasswordUseCase;
import com.live_commerce.user.application.port.out.LoadUserPort;
import com.live_commerce.user.application.port.out.SaveUserPort;
import com.live_commerce.user.application.port.out.SendMailPort;
import com.live_commerce.user.domain.model.User;
import com.live_commerce.user.infrastructure.common.PasswordGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ResetPasswordService implements ResetPasswordUseCase {

	private final LoadUserPort loadUserPort;
	private final SaveUserPort saveUserPort;
	private final SendMailPort sendMailPort;
	private final PasswordEncoder passwordEncoder;

	@Override
	public void resetPasswordAndSendTempPassword(String username, String email) {
		User user = loadUserPort.loadByUsernameAndEmail(username, email)
			.filter(u -> !u.isDeleted())
			.orElseThrow(() -> new CustomException(UserExceptionCode.USER_NOT_FOUND));

		String tempPassword = PasswordGenerator.generateTempPassword(10);
		user.changePassword(passwordEncoder.encode(tempPassword));
		saveUserPort.save(user);

		sendMailPort.sendTemporaryPassword(email, tempPassword);
	}
}
