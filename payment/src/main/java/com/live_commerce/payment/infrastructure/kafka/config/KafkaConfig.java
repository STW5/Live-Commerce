package com.live_commerce.payment.infrastructure.kafka.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class KafkaConfig {

	@Value("${spring.kafka.bootstrap-servers}")
	private String bootstrapServers;

	@Value("${spring.application.name}")
	private String groupId;

	/**
	 * Kafka Consumer Factory with DLQ support
	 */
	@Bean
	public ConsumerFactory<String, Object> consumerFactory() {
		Map<String, Object> props = new HashMap<>();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

		// ErrorHandlingDeserializer로 감싸서 역직렬화 오류 처리
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
		props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
		props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

		props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
		props.put(JsonDeserializer.TYPE_MAPPINGS,
			"order-failed:com.live_commerce.events.order.OrderFailedEvent");

		return new DefaultKafkaConsumerFactory<>(props);
	}

	/**
	 * Kafka Listener Container Factory with Retry and DLQ
	 */
	@Bean
	public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
		ConsumerFactory<String, Object> consumerFactory,
		KafkaTemplate<String, Object> kafkaTemplate
	) {
		ConcurrentKafkaListenerContainerFactory<String, Object> factory =
			new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConsumerFactory(consumerFactory);
		factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

		// Exponential Backoff: 초기 1초, 최대 10초, 최대 3번 재시도
		ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
		backOff.setMaxElapsedTime(10000L);

		DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, exception) -> {
			// 3번 재시도 실패 후 DLQ로 전송
			log.error("[Kafka] 메시지 처리 최종 실패, DLQ로 전송 - topic: {}, offset: {}, key: {}",
				record.topic(), record.offset(), record.key());

			String dlqTopic = record.topic() + ".DLQ";
			String key = record.key() != null ? record.key().toString() : null;
			Object value = record.value();

			kafkaTemplate.send(dlqTopic, key, value);
		}, backOff);

		// 특정 예외는 재시도하지 않고 바로 DLQ로 전송
		errorHandler.addNotRetryableExceptions(
			IllegalArgumentException.class,
			ClassCastException.class
		);

		factory.setCommonErrorHandler(errorHandler);

		return factory;
	}

	/**
	 * Kafka Producer Factory
	 */
	@Bean
	public ProducerFactory<String, Object> producerFactory() {
		Map<String, Object> props = new HashMap<>();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
		props.put(JsonSerializer.TYPE_MAPPINGS,
			"payment-completed:com.live_commerce.events.payment.PaymentCompletedEvent," +
				"payment-failed:com.live_commerce.events.payment.PaymentFailedEvent");

		return new DefaultKafkaProducerFactory<>(props);
	}

	@Bean
	public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
		return new KafkaTemplate<>(producerFactory);
	}
}
