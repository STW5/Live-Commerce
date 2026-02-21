package com.live_commerce.user.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.live_commerce.user.application.port.in.LogoutUseCase;
import com.live_commerce.user.application.port.out.ManageUserTokenPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LogoutService implements LogoutUseCase {

	private final ManageUserTokenPort manageUserTokenPort;

	@Override
	public void logout(UUID userId) {
		manageUserTokenPort.deleteRefreshToken(userId);
	}
}
