package com.live_commerce.payment.infrastructure.kafka.consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.live_commerce.payment.infrastructure.kafka.event.OrderFailedEvent;
import com.live_commerce.payment.infrastructure.notification.SlackNotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Dead Letter Queue Consumer
 * 재시도 실패 후 DLQ로 들어온 메시지를 처리하고 알림을 발송합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentDLQConsumer {

	@Autowired(required = false)
	private SlackNotificationService slackNotificationService;

	/**
	 * order-failed.DLQ 토픽의 메시지 소비
	 */
	@KafkaListener(topics = "order-failed.DLQ", groupId = "${spring.application.name}-dlq")
	public void listenOrderFailedDLQ(
		@Payload OrderFailedEvent event,
		@Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
		@Header(KafkaHeaders.OFFSET) long offset,
		@Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exceptionMessage,
		Acknowledgment acknowledgment
	) {
		log.error(
			"""
			====================================
			[DLQ] 보상 트랜잭션 최종 실패
			====================================
			Topic: {}
			Offset: {}
			OrderId: {}
			Message: {}
			Exception: {}
			====================================
			⚠️ 수동 개입 필요: 운영팀에게 알림을 발송합니다.
			====================================
			""",
			topic, offset, event.orderId(), event.message(), exceptionMessage
		);

		try {
			// 1. 데이터베이스에 실패 로그 기록 (향후 구현)
			// saveFailedCompensationLog(event, exceptionMessage);

			// 2. 알림 발송 (Slack, Email, PagerDuty 등)
			sendCriticalAlert(event, topic, offset, exceptionMessage);

			// 3. ACK (DLQ 메시지는 재시도하지 않음)
			if (acknowledgment != null) {
				acknowledgment.acknowledge();
			}

			log.info("[DLQ] 알림 발송 완료 - orderId: {}", event.orderId());

		} catch (Exception e) {
			log.error("[DLQ] 알림 발송 실패 - orderId: {}, error: {}", event.orderId(), e.getMessage(), e);

			// 알림 발송도 실패하면 ACK 하지 않고 다시 시도
			// (무한 루프 방지를 위해 별도 모니터링 필요)
		}
	}

	/**
	 * 중요 알림 발송
	 */
	private void sendCriticalAlert(OrderFailedEvent event, String topic, long offset, String exceptionMessage) {
		String alertTitle = "🚨 [CRITICAL] 결제 보상 트랜잭션 최종 실패";

		String alertMessage = String.format(
			"""
			주문 ID: %s
			메시지: %s
			토픽: %s
			오프셋: %d
			예외: %s

			⚠️ 즉시 확인이 필요합니다!
			1. 주문 상태 확인
			2. 결제 상태 확인 (KakaoPay)
			3. 수동 환불 처리 여부 결정
			""",
			event.orderId(),
			event.message(),
			topic,
			offset,
			exceptionMessage != null ? exceptionMessage : "N/A"
		);

		log.warn("[DLQ] 알림 내용:\n{}{}", alertTitle, alertMessage);

		// Slack 알림 발송 (설정된 경우에만)
		if (slackNotificationService != null) {
			try {
				slackNotificationService.sendCriticalAlert(alertTitle, alertMessage);
				log.info("[DLQ] Slack 알림 발송 완료 - orderId: {}", event.orderId());
			} catch (Exception e) {
				log.error("[DLQ] Slack 알림 발송 실패 - orderId: {}, error: {}",
					event.orderId(), e.getMessage(), e);
			}
		} else {
			log.warn("[DLQ] Slack 알림 서비스가 비활성화되어 있습니다.");
		}

		// 추가 알림 채널 구현 가능 (Email, PagerDuty 등)
		// emailNotificationService.send("ops@company.com", alertTitle, alertMessage);
	}
}
