package com.live_commerce.user.application.port.in;

import com.live_commerce.user.application.dto.command.SignUpCommand;
import com.live_commerce.user.application.dto.result.SignUpResult;

public interface SignUpUseCase {
	SignUpResult signUp(SignUpCommand command);
}
