package com.live_commerce.payment.infrastructure.kafka.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.live_commerce.payment.application.exception.CustomException;
import com.live_commerce.payment.application.exception.KakaoPayApiException;
import com.live_commerce.payment.application.service.PaymentServiceV2;
import com.live_commerce.payment.infrastructure.kafka.event.OrderFailedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

	private final PaymentServiceV2 paymentServiceV2;

	@KafkaListener(topics = "order-failed", groupId = "${spring.application.name}")
	public void listenOrderFailed(
		@Payload OrderFailedEvent event,
		@Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
		@Header(KafkaHeaders.OFFSET) long offset,
		Acknowledgment acknowledgment
	) {
		log.info("[Kafka] 주문 실패 이벤트 수신 - orderId: {}, message: {}, topic: {}, offset: {}",
			event.orderId(), event.message(), topic, offset);

		try {
			// 보상 트랜잭션 실행
			paymentServiceV2.compensateRefundByOrderId(event.orderId(), event.message());

			// 성공 시 ACK
			if (acknowledgment != null) {
				acknowledgment.acknowledge();
			}

			log.info("[Kafka] 보상 트랜잭션 완료 및 ACK - orderId: {}", event.orderId());

		} catch (CustomException e) {
			log.error("[Kafka] 보상 처리 실패 (비즈니스 오류) - orderId: {}, code: {}, message: {}",
				event.orderId(), e.getExceptionCode(), e.getMessage());

			// 비즈니스 예외는 재시도해도 성공 가능성 낮음 -> ACK 처리 (DLQ로 이동)
			if (acknowledgment != null) {
				acknowledgment.acknowledge();
			}
			throw e; // DefaultErrorHandler가 DLQ로 전송

		} catch (KakaoPayApiException e) {
			log.error("[Kafka] 카카오페이 환불 실패 (일시적 오류 가능) - orderId: {}, message: {}",
				event.orderId(), e.getMessage());

			// 네트워크 오류 등 일시적 문제 가능 -> 재시도 (ACK 하지 않음)
			throw e; // DefaultErrorHandler가 Exponential Backoff로 재시도

		} catch (Exception e) {
			log.error("[Kafka] 예상치 못한 오류 발생 - orderId: {}, message: {}",
				event.orderId(), e.getMessage(), e);

			// 알 수 없는 오류 -> 재시도 (ACK 하지 않음)
			throw e;
		}
	}
}

