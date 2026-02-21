package com.live_commerce.user.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.user.application.dto.command.UpdateUserCommand;
import com.live_commerce.user.application.dto.result.UserUpdateResult;
import com.live_commerce.user.application.exception.CustomException;
import com.live_commerce.user.application.exception.UserExceptionCode;
import com.live_commerce.user.application.port.in.UpdateUserUseCase;
import com.live_commerce.user.application.port.out.LoadUserPort;
import com.live_commerce.user.application.port.out.SaveUserPort;
import com.live_commerce.user.domain.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class UpdateUserService implements UpdateUserUseCase {

	private final LoadUserPort loadUserPort;
	private final SaveUserPort saveUserPort;

	@Override
	public UserUpdateResult updateUser(UpdateUserCommand command) {
		if (!command.requesterIsMaster() && !command.targetUserId().equals(command.requesterId())) {
			throw new CustomException(UserExceptionCode.FORBIDDEN);
		}
		if (!command.requesterIsMaster() && command.userRole() != null) {
			throw new CustomException(UserExceptionCode.ROLE_CHANGE_FORBIDDEN);
		}

		User user = loadUserPort.loadById(command.targetUserId())
			.orElseThrow(() -> new CustomException(UserExceptionCode.USER_NOT_FOUND));

		if (!command.requesterIsMaster() && user.isDeleted()) {
			throw new CustomException(UserExceptionCode.DELETED_USER);
		}

		user.updateUser(
			command.encodedPassword() != null ? command.encodedPassword() : user.getPassword(),
			command.email() != null ? command.email() : user.getEmail(),
			command.nickname() != null ? command.nickname() : user.getNickname(),
			command.alarmConsent() != null ? command.alarmConsent() : user.isAlarmConsent(),
			command.userRole() != null ? command.userRole() : user.getUserRole()
		);

		User saved = saveUserPort.save(user);
		return UserUpdateResult.from(saved);
	}
}
