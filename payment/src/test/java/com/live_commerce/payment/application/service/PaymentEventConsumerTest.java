package com.live_commerce.payment.application.service;

import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.live_commerce.payment.application.port.in.CompensatePaymentUseCase;
import com.live_commerce.payment.application.port.in.CompensatePaymentUseCase.CompensatePaymentCommand;
import com.live_commerce.payment.infrastructure.adapter.in.kafka.OrderFailedKafkaConsumer;
import com.live_commerce.payment.infrastructure.kafka.event.OrderFailedEvent;

public class PaymentEventConsumerTest {

	private CompensatePaymentUseCase compensatePaymentUseCase;
	private OrderFailedKafkaConsumer consumer;

	@BeforeEach
	void setUp() {
		compensatePaymentUseCase = mock(CompensatePaymentUseCase.class);
		consumer = new OrderFailedKafkaConsumer(compensatePaymentUseCase);
	}

	@DisplayName("OrderFailedEvent 수신 시 보상 트랜잭션 유스케이스를 호출한다")
	@Test
	void shouldCallCompensateUseCaseOnOrderFailedEvent() {
		UUID orderId = UUID.randomUUID();
		String reason = "재고 부족";
		OrderFailedEvent event = new OrderFailedEvent(orderId, reason);

		consumer.listenOrderFailed(event, "order-failed", 0L, null);

		verify(compensatePaymentUseCase).compensate(new CompensatePaymentCommand(orderId, reason));
	}
}
