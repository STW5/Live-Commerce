package com.live_commerce.user.infrastructure.adapter.event;

import java.util.UUID;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.live_commerce.user.application.port.out.PublishUserEventPort;
import com.live_commerce.user.infrastructure.kafka.event.FirstJoinCouponEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaUserEventPublisherAdapter implements PublishUserEventPort {

	private final KafkaTemplate<String, FirstJoinCouponEvent> kafkaTemplate;
	private static final String FIRST_COUPON_TOPIC = "first-join-coupon";

	@Override
	public void publishFirstJoinCouponEvent(UUID userId) {
		kafkaTemplate.send(FIRST_COUPON_TOPIC, userId.toString(), new FirstJoinCouponEvent(userId));
		log.info("[Kafka] 첫가입 쿠폰 이벤트 전송 완료: {}", userId);
	}
}
