package com.live_commerce.user.infrastructure.adapter.mail;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.live_commerce.user.application.exception.CustomException;
import com.live_commerce.user.application.exception.UserExceptionCode;
import com.live_commerce.user.application.port.out.SendMailPort;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JavaMailNotificationAdapter implements SendMailPort {

	private final JavaMailSender mailSender;

	@Async
	@Override
	public void sendVerificationCode(String email, String code) {
		String subject = "[LiveCommerce] 아이디 찾기 인증번호 안내";
		String body = "인증번호는 다음과 같습니다:\n\n" + code + "\n\n5분 내로 입력해주세요.";
		sendMail(email, subject, body);
	}

	@Async
	@Override
	public void sendTemporaryPassword(String email, String tempPassword) {
		String subject = "[LiveCommerce] 임시 비밀번호 안내";
		String body = "요청하신 임시 비밀번호는 다음과 같습니다:\n\n" +
			tempPassword + "\n\n로그인 후 반드시 비밀번호를 변경해주세요.";
		sendMail(email, subject, body);
	}

	private void sendMail(String email, String subject, String body) {
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
}
