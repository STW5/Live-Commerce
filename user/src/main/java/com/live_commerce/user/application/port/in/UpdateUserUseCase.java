package com.live_commerce.user.application.port.in;

import com.live_commerce.user.application.dto.command.UpdateUserCommand;
import com.live_commerce.user.application.dto.result.UserUpdateResult;

public interface UpdateUserUseCase {
	UserUpdateResult updateUser(UpdateUserCommand command);
}
