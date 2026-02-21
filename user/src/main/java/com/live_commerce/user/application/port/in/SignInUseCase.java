package com.live_commerce.user.application.port.in;

import com.live_commerce.user.application.dto.command.SignInCommand;
import com.live_commerce.user.application.dto.result.SignInResult;

public interface SignInUseCase {
	SignInResult signIn(SignInCommand command);
}
