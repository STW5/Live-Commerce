package com.live_commerce.user.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.user.application.dto.result.UserGetResult;
import com.live_commerce.user.application.exception.CustomException;
import com.live_commerce.user.application.exception.UserExceptionCode;
import com.live_commerce.user.application.port.in.GetUserUseCase;
import com.live_commerce.user.application.port.out.LoadUserPort;
import com.live_commerce.user.domain.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetUserService implements GetUserUseCase {

	private final LoadUserPort loadUserPort;

	@Override
	public UserGetResult getUser(UUID userId, UUID requesterId, boolean requesterIsMaster) {
		if (!requesterIsMaster && !userId.equals(requesterId)) {
			throw new CustomException(UserExceptionCode.FORBIDDEN);
		}

		User user = loadUserPort.loadById(userId)
			.orElseThrow(() -> new CustomException(UserExceptionCode.USER_NOT_FOUND));

		// 마스터는 삭제 유저도 조회 가능
		if (!requesterIsMaster && user.isDeleted()) {
			throw new CustomException(UserExceptionCode.DELETED_USER);
		}

		return UserGetResult.from(user);
	}
}
