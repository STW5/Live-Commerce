package com.live_commerce.user.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.user.application.exception.CustomException;
import com.live_commerce.user.application.exception.UserExceptionCode;
import com.live_commerce.user.application.port.in.DeleteUserUseCase;
import com.live_commerce.user.application.port.out.LoadUserPort;
import com.live_commerce.user.application.port.out.SaveUserPort;
import com.live_commerce.user.domain.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class DeleteUserService implements DeleteUserUseCase {

	private final LoadUserPort loadUserPort;
	private final SaveUserPort saveUserPort;

	@Override
	public void deleteUser(UUID userId, UUID requesterId, boolean requesterIsMaster) {
		if (!requesterIsMaster && !userId.equals(requesterId)) {
			throw new CustomException(UserExceptionCode.FORBIDDEN);
		}

		User user = loadUserPort.loadById(userId)
			.filter(u -> !u.isDeleted())
			.orElseThrow(() -> new CustomException(UserExceptionCode.USER_NOT_FOUND));

		user.softDelete(requesterId.toString());
		saveUserPort.save(user);
	}
}
