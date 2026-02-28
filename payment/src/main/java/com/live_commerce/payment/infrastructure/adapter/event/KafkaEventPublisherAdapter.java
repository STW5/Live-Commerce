package com.live_commerce.payment.infrastructure.adapter.event;

import com.live_commerce.payment.application.port.out.PublishPaymentEventPort;
import com.live_commerce.payment.infrastructure.outbox.OutboxEventHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Kafka 이벤트 발행 어댑터 (Outbox 패턴)
 * - PublishPaymentEventPort 구현
 * - Outbox 패턴으로 이벤트 저장하여 DB 트랜잭션과 원자성 보장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisherAdapter implements PublishPaymentEventPort {

	private final OutboxEventHelper outboxEventHelper;

	@Override
	public void publishCompleted(PaymentCompletedEvent event) {
		com.live_commerce.events.payment.PaymentCompletedEvent kafkaEvent =
			com.live_commerce.events.payment.PaymentCompletedEvent.of(
				event.orderId(), event.message(), event.amount());
		outboxEventHelper.saveEvent("PAYMENT", event.orderId(),
				"PAYMENT_COMPLETED", "payment-completed", kafkaEvent);
		log.info("[KafkaEventPublisherAdapter] payment-completed Outbox 저장 - orderId: {}", event.orderId());
	}

	@Override
	public void publishFailed(PaymentFailedEvent event) {
		com.live_commerce.events.payment.PaymentFailedEvent kafkaEvent =
			com.live_commerce.events.payment.PaymentFailedEvent.of(
				event.orderId(), event.message());
		outboxEventHelper.saveEvent("PAYMENT", event.orderId(),
				"PAYMENT_FAILED", "payment-failed", kafkaEvent);
		log.info("[KafkaEventPublisherAdapter] payment-failed Outbox 저장 - orderId: {}", event.orderId());
	}
}
