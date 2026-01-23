package com.live_commerce.payment.infrastructure.notification;

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
 * Slack Webhook을 통한 알림 발송 서비스
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
	 * Slack에 긴급 알림 발송
	 */
	public void sendCriticalAlert(String title, String message) {
		if (slackWebhookUrl == null || slackWebhookUrl.isBlank()) {
			log.warn("[Slack] Webhook URL이 설정되지 않았습니다. 알림을 건너뜁니다.");
			return;
		}

		try {
			String payload = buildSlackPayload(title, message, "#ff0000"); // 빨간색

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<String> request = new HttpEntity<>(payload, headers);

			restTemplate.postForEntity(slackWebhookUrl, request, String.class);

			log.info("[Slack] 알림 발송 완료 - title: {}", title);

		} catch (Exception e) {
			log.error("[Slack] 알림 발송 실패 - title: {}, error: {}", title, e.getMessage(), e);
		}
	}

	/**
	 * Slack에 경고 알림 발송
	 */
	public void sendWarningAlert(String title, String message) {
		if (slackWebhookUrl == null || slackWebhookUrl.isBlank()) {
			log.warn("[Slack] Webhook URL이 설정되지 않았습니다. 알림을 건너뜁니다.");
			return;
		}

		try {
			String payload = buildSlackPayload(title, message, "#ffa500"); // 주황색

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<String> request = new HttpEntity<>(payload, headers);

			restTemplate.postForEntity(slackWebhookUrl, request, String.class);

			log.info("[Slack] 경고 알림 발송 완료 - title: {}", title);

		} catch (Exception e) {
			log.error("[Slack] 경고 알림 발송 실패 - title: {}, error: {}", title, e.getMessage(), e);
		}
	}

	/**
	 * Slack 메시지 페이로드 생성 (Attachment 형식)
	 */
	private String buildSlackPayload(String title, String message, String color) {
		// Slack Webhook Attachment format
		return String.format("""
			{
				"attachments": [
					{
						"color": "%s",
						"title": "%s",
						"text": "%s",
						"footer": "Live Commerce Payment Service",
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

	/**
	 * JSON 특수문자 이스케이프
	 */
	private String escapeJson(String text) {
		if (text == null) {
			return "";
		}
		return text
			.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}
}
