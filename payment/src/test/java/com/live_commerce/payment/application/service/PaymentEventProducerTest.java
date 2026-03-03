package com.live_commerce.payment.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import com.live_commerce.events.payment.PaymentCompletedEvent;
import com.live_commerce.payment.infrastructure.kafka.producer.PaymentEventProducer;

public class PaymentEventProducerTest {

	@SuppressWarnings("unchecked")
	private KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
	private PaymentEventProducer producer;

	@BeforeEach
	void setUp() {
		producer = new PaymentEventProducer(kafkaTemplate);
	}

	@DisplayName("결제 완료 이벤트를 Kafka로 발행한다")
	@Test
	void shouldPublishPaymentCompletedEvent() {
		UUID orderId = UUID.randomUUID();
		PaymentCompletedEvent event =
			new PaymentCompletedEvent(orderId, "결제완료", BigDecimal.valueOf(990000, 2));

		producer.sendPaymentCompleted(event);

		ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);

		verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());

		assertThat(topicCaptor.getValue()).isEqualTo("payment-completed");
		assertThat(keyCaptor.getValue()).isEqualTo(orderId.toString());

		PaymentCompletedEvent captured = (PaymentCompletedEvent) valueCaptor.getValue();
		assertThat(captured.orderId()).isEqualTo(orderId);
		assertThat(captured.message()).isEqualTo("결제완료");
		assertThat(captured.finalPaidPrice()).isEqualByComparingTo(BigDecimal.valueOf(9900.00));
	}
}
