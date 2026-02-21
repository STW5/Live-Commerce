package com.live_commerce.user.application.service;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.live_commerce.user.application.exception.CustomException;
import com.live_commerce.user.application.exception.UserExceptionCode;
import com.live_commerce.user.infrastructure.common.RedisUtil;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

/**
 * @deprecated Use {@code JavaMailNotificationAdapter} (implements {@code SendMailPort}) instead.
 */
@Deprecated(since = "hexagonal-ddd-user", forRemoval = true)
@Service
@RequiredArgsConstructor
public class MailService {

	private final JavaMailSender mailSender;
	private final RedisUtil redisUtil;

	@Async
	public void sendVerificationCode(String email) {
		String code = generateCode();
		saveVerificationCode(email, code);

		String subject = "[LiveCommerce] 아이디 찾기 인증번호 안내";
		String body = buildTextBody(code);

		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

			helper.setTo(email);
			helper.setSubject(subject);
			helper.setText(body);

			mailSender.send(message);
		} catch (MessagingException e) {
			throw new CustomException(UserExceptionCode.MAIL_SEND_FAILED);
		}
	}

	@Async
	public void sendTemporaryPassword(String email, String tempPassword) {
		String subject = "[LiveCommerce] 임시 비밀번호 안내";
		String body = buildTempPasswordBody(tempPassword);

		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

			helper.setTo(email);
			helper.setSubject(subject);
			helper.setText(body);

			mailSender.send(message);
		} catch (MessagingException e) {
			throw new CustomException(UserExceptionCode.MAIL_SEND_FAILED);
		}
	}


	private void saveVerificationCode(String email, String code) {
		redisUtil.setDataExpire(email, code, 300);
	}

	private String generateCode() {
		int code = ThreadLocalRandom.current().nextInt(100_000, 1_000_000); // 6자리
		return String.valueOf(code);
	}

	private String buildTextBody(String code) {
		return "인증번호는 다음과 같습니다:\n\n" + code + "\n\n5분 내로 입력해주세요.";
	}

	private String buildTempPasswordBody(String tempPassword) {
		return "요청하신 임시 비밀번호는 다음과 같습니다:\n\n" +
			tempPassword + "\n\n로그인 후 반드시 비밀번호를 변경해주세요.";
	}
}
