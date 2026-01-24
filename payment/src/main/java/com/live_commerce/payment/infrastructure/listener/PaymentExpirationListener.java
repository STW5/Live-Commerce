package com.live_commerce.payment.infrastructure.listener;

import java.util.UUID;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.payment.domain.model.PaymentStatus;
import com.live_commerce.payment.domain.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis TTL 만료 리스너
 * - 결제 타임아웃 시 자동으로 FAILED 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpirationListener implements MessageListener {

	private final PaymentRepository paymentRepository;

	@Override
	@Transactional
	public void onMessage(Message message, byte[] pattern) {
		String expiredKey = new String(message.getBody());

		if (expiredKey.startsWith("payment:expire:")) {
			try {
				UUID orderId = UUID.fromString(expiredKey.replace("payment:expire:", ""));
				log.info("Redis TTL 만료 감지됨 – orderId: {}", orderId);

				paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.PENDING)
					.ifPresent(payment -> {
						payment.fail();
						log.info("자동 실패 처리 완료 – paymentId: {}, orderId: {}", payment.getId(), orderId);
					});
			} catch (Exception e) {
				log.error("Redis TTL 처리 중 예외 발생 – key: {}", expiredKey, e);
			}
		}
	}
}
