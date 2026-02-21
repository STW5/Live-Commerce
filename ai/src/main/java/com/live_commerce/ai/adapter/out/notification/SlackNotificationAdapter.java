package com.live_commerce.ai.adapter.out.notification;

import org.springframework.stereotype.Component;

import com.live_commerce.ai.domain.port.out.AiNotificationPort;
import com.live_commerce.ai.infrastructure.slack.SlackSender;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SlackNotificationAdapter implements AiNotificationPort {

	private final SlackSender slackSender;

	@Override
	public void sendAnalysisResult(String userId, String message) {
		slackSender.sendMessage(userId, message);
	}
}
