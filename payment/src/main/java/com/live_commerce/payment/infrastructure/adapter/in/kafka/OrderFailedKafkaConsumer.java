package com.live_commerce.payment.infrastructure.adapter.in.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.live_commerce.payment.application.exception.CustomException;
import com.live_commerce.payment.application.exception.KakaoPayApiException;
import com.live_commerce.payment.application.port.in.CompensatePaymentUseCase;
import com.live_commerce.payment.application.port.in.CompensatePaymentUseCase.CompensatePaymentCommand;
import com.live_commerce.payment.infrastructure.kafka.event.OrderFailedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주문 실패 Kafka 컨슈머 - Hexagonal Inbound Adapter
 * - CompensatePaymentUseCase를 통한 보상 트랜잭션 처리
 * - 기존 {@code PaymentEventConsumer}(@Deprecated) 대체
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFailedKafkaConsumer {

	private final CompensatePaymentUseCase compensatePaymentUseCase;

	@KafkaListener(topics = "order-failed", groupId = "${spring.application.name}-hexagonal")
	public void listenOrderFailed(
		@Payload OrderFailedEvent event,
		@Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
		@Header(KafkaHeaders.OFFSET) long offset,
		Acknowledgment acknowledgment
	) {
		log.info("[Kafka] 주문 실패 이벤트 수신 - orderId: {}, message: {}, topic: {}, offset: {}",
			event.orderId(), event.message(), topic, offset);

		try {
			compensatePaymentUseCase.compensate(
				new CompensatePaymentCommand(event.orderId(), event.message())
			);

			if (acknowledgment != null) {
				acknowledgment.acknowledge();
			}

			log.info("[Kafka] 보상 트랜잭션 완료 및 ACK - orderId: {}", event.orderId());

		} catch (CustomException e) {
			log.error("[Kafka] 보상 처리 실패 (비즈니스 오류) - orderId: {}, code: {}, message: {}",
				event.orderId(), e.getExceptionCode(), e.getMessage());

			if (acknowledgment != null) {
				acknowledgment.acknowledge();
			}
			throw e;

		} catch (KakaoPayApiException e) {
			log.error("[Kafka] 카카오페이 환불 실패 (일시적 오류 가능) - orderId: {}, message: {}",
				event.orderId(), e.getMessage());
			throw e;

		} catch (Exception e) {
			log.error("[Kafka] 예상치 못한 오류 발생 - orderId: {}, message: {}",
				event.orderId(), e.getMessage(), e);
			throw e;
		}
	}
}
