package com.live_commerce.user.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.user.application.exception.CustomException;
import com.live_commerce.user.application.exception.UserExceptionCode;
import com.live_commerce.user.application.port.in.ApproveUserUseCase;
import com.live_commerce.user.application.port.out.LoadUserPort;
import com.live_commerce.user.application.port.out.SaveUserPort;
import com.live_commerce.user.domain.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ApproveUserService implements ApproveUserUseCase {

	private final LoadUserPort loadUserPort;
	private final SaveUserPort saveUserPort;

	@Override
	public void approveUser(UUID userId) {
		User user = loadUserPort.loadById(userId)
			.orElseThrow(() -> new CustomException(UserExceptionCode.USER_NOT_FOUND));
		user.approve();
		saveUserPort.save(user);
	}
}
