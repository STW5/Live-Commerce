package com.live_commerce.user.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.user.application.dto.command.SignUpCommand;
import com.live_commerce.user.application.dto.result.SignUpResult;
import com.live_commerce.user.application.exception.CustomException;
import com.live_commerce.user.application.exception.UserExceptionCode;
import com.live_commerce.user.application.port.in.SignUpUseCase;
import com.live_commerce.user.application.port.out.LoadUserPort;
import com.live_commerce.user.application.port.out.PublishUserEventPort;
import com.live_commerce.user.application.port.out.SaveUserPort;
import com.live_commerce.user.domain.model.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SignUpService implements SignUpUseCase {

	private final LoadUserPort loadUserPort;
	private final SaveUserPort saveUserPort;
	private final PublishUserEventPort publishUserEventPort;

	@Override
	public SignUpResult signUp(SignUpCommand command) {
		if (loadUserPort.existsByEmail(command.email())) {
			throw new CustomException(UserExceptionCode.DUPLICATE_EMAIL);
		}

		User user = User.of(
			command.username(), command.encodedPassword(), command.email(),
			command.nickname(), command.alarmConsent(), command.userRole(), command.approved()
		);

		// 반환값 캡처 필수 — DB 생성 UUID가 savedUser에만 존재
		User savedUser = saveUserPort.save(user);

		// Kafka 이벤트 발행 (첫가입 쿠폰)
		try {
			publishUserEventPort.publishFirstJoinCouponEvent(savedUser.getUserId());
		} catch (Exception e) {
			log.warn("첫가입 쿠폰 이벤트 발행 실패 - userId: {}", savedUser.getUserId(), e);
		}

		return SignUpResult.from(savedUser);
	}
}
