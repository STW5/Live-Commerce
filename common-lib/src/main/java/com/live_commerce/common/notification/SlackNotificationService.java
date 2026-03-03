package com.live_commerce.common.notification;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Slack Webhook을 통한 알림 발송 서비스 (공통 모듈)
 * - notification.slack.enabled=true 설정 시 활성화
 * - 각 서비스의 application.yml에서 webhook-url 설정 필요
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackNotificationService {

	private final RestTemplate restTemplate;

	@Value("${notification.slack.webhook-url:}")
	private String slackWebhookUrl;

	/**
	 * Slack에 긴급 알림 발송 (빨간색)
	 */
	public void sendCriticalAlert(String title, String message) {
		send(title, message, "#ff0000");
	}

	/**
	 * Slack에 경고 알림 발송 (주황색)
	 */
	public void sendWarningAlert(String title, String message) {
		send(title, message, "#ffa500");
	}

	private void send(String title, String message, String color) {
		if (slackWebhookUrl == null || slackWebhookUrl.isBlank()) {
			log.warn("[Slack] Webhook URL이 설정되지 않았습니다. 알림을 건너뜁니다.");
			return;
		}

		try {
			String payload = buildSlackPayload(title, message, color);

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<String> request = new HttpEntity<>(payload, headers);

			restTemplate.postForEntity(slackWebhookUrl, request, String.class);
			log.info("[Slack] 알림 발송 완료 - title: {}", title);

		} catch (Exception e) {
			log.error("[Slack] 알림 발송 실패 - title: {}, error: {}", title, e.getMessage(), e);
		}
	}

	private String buildSlackPayload(String title, String message, String color) {
		return String.format("""
				{
					"attachments": [
						{
							"color": "%s",
							"title": "%s",
							"text": "%s",
							"footer": "Live Commerce MSA",
							"ts": %d
						}
					]
				}
				""",
			color,
			escapeJson(title),
			escapeJson(message),
			System.currentTimeMillis() / 1000
		);
	}

	private String escapeJson(String text) {
		if (text == null) return "";
		return text
			.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}
}
