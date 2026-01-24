package com.live_commerce.payment.infrastructure.adapter.event;

import org.springframework.stereotype.Component;

import com.live_commerce.payment.application.port.out.PublishPaymentEventPort;
import com.live_commerce.payment.infrastructure.kafka.producer.PaymentEventProducer;

import lombok.RequiredArgsConstructor;

/**
 * Kafka 이벤트 발행 어댑터
 * - PublishPaymentEventPort 구현
 * - PaymentEventProducer(기존 구현)를 활용하여 Kafka로 이벤트 발행
 * - Port DTO → Infrastructure Event 변환
 */
@Component
@RequiredArgsConstructor
public class KafkaEventPublisherAdapter implements PublishPaymentEventPort {

	private final PaymentEventProducer paymentEventProducer;

	@Override
	public void publishCompleted(PaymentCompletedEvent event) {
		paymentEventProducer.sendPaymentCompleted(
			new com.live_commerce.payment.infrastructure.kafka.event.PaymentCompletedEvent(
				event.orderId(),
				event.message(),
				event.amount()
			)
		);
	}

	@Override
	public void publishFailed(PaymentFailedEvent event) {
		paymentEventProducer.sendPaymentFailed(
			new com.live_commerce.payment.infrastructure.kafka.event.PaymentFailedEvent(
				event.orderId(),
				event.message()
			)
		);
	}
}
