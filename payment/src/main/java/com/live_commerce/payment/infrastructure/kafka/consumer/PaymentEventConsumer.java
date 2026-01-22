package com.live_commerce.payment.infrastructure.kafka.consumer;

import org.springframework.kafka.annotation.KafkaListener;
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
	public void listenOrderFailed(OrderFailedEvent event) {
		log.info("[Kafka] 주문 실패 이벤트 수신: orderId = {}, message = {}", event.orderId(), event.message());

		try {
			paymentServiceV2.compensateRefundByOrderId(event.orderId(), event.message());
			log.info("[Kafka] 보상 트랜잭션 완료: orderId = {}", event.orderId());

		} catch (CustomException e) {
			log.error("[Kafka] 보상 처리 실패 (비즈니스 오류) - orderId: {}, code: {}, message: {}",
				event.orderId(), e.getExceptionCode(), e.getMessage());
			// DLQ로 전송하거나 재시도 필요

		} catch (KakaoPayApiException e) {
			log.error("[Kafka] 카카오페이 환불 실패 (수동 개입 필요) - orderId: {}, message: {}",
				event.orderId(), e.getMessage(), e);
			// 알림 발송 필요 (Slack, PagerDuty 등)

		} catch (Exception e) {
			log.error("[Kafka] 예상치 못한 오류 발생 - orderId: {}, message: {}",
				event.orderId(), e.getMessage(), e);
			// 재시도 또는 수동 개입 필요
		}
	}
}

