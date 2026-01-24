package com.live_commerce.payment.infrastructure.adapter.event;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import com.live_commerce.payment.application.port.out.ManagePaymentExpirationPort;

import lombok.RequiredArgsConstructor;

/**
 * Redis 결제 만료 관리 어댑터
 * - ManagePaymentExpirationPort 구현
 * - Redisson을 활용하여 TTL 기반 결제 타임아웃 관리
 */
@Component
@RequiredArgsConstructor
public class RedisPaymentExpirationAdapter implements ManagePaymentExpirationPort {

	private static final String PAYMENT_EXPIRE_KEY_PREFIX = "payment:expire:";

	private final RedissonClient redissonClient;

	@Override
	public void setExpiration(UUID orderId, UUID paymentId, long timeout, TimeUnit unit) {
		String key = PAYMENT_EXPIRE_KEY_PREFIX + orderId;
		RBucket<String> bucket = redissonClient.getBucket(key);
		bucket.set(paymentId.toString(), timeout, unit);
	}

	@Override
	public void removeExpiration(UUID orderId) {
		String key = PAYMENT_EXPIRE_KEY_PREFIX + orderId;
		RBucket<String> bucket = redissonClient.getBucket(key);
		bucket.delete();
	}
}
