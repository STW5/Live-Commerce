package com.live_commerce.payment.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.live_commerce.payment.application.port.in.ReadyPaymentUseCase;
import com.live_commerce.payment.application.port.in.ReadyPaymentUseCase.ReadyPaymentCommand;
import com.live_commerce.payment.application.port.out.ManagePaymentExpirationPort;
import com.live_commerce.payment.application.port.out.PaymentGatewayPort;
import com.live_commerce.payment.application.port.out.PaymentGatewayPort.PaymentReadyResult;
import com.live_commerce.payment.infrastructure.adapter.persistence.PaymentJpaRepository;

@SpringBootTest
@ActiveProfiles("test")
public class PaymentDuplicateRequestTest {

	@Autowired
	private ReadyPaymentUseCase readyPaymentUseCase;

	@Autowired
	private PaymentJpaRepository paymentJpaRepository;

	@MockitoBean
	private PaymentGatewayPort paymentGatewayPort;

	@MockitoBean
	private ManagePaymentExpirationPort managePaymentExpirationPort;

	@MockitoBean
	private RedissonClient redissonClient;
	@MockitoBean
	private RedisMessageListenerContainer redisContainer;

	@BeforeEach
	void setUp() {
		// 분산 락 mock 설정 - 실제 Redisson 없이 테스트 가능
		RLock mockLock = mock(RLock.class);
		try {
			when(mockLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		when(mockLock.isHeldByCurrentThread()).thenReturn(true);
		when(redissonClient.getLock(anyString())).thenReturn(mockLock);

		when(paymentGatewayPort.ready(any(), any(), any(), any()))
			.thenReturn(new PaymentReadyResult("T123456789", "https://kakao.pay.pc", "2025-04-17T21:20:00"));
	}

	@DisplayName("동시 결제 요청 시 서비스 레벨 중복 결제 방지 테스트")
	@Test
	void duplicatePaymentRequest_should_create_only_one_payment() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();
		ReadyPaymentCommand command = new ReadyPaymentCommand(userId, orderId, BigDecimal.valueOf(9999), "중복 테스트");

		int threadCount = 5;
		CountDownLatch latch = new CountDownLatch(threadCount);
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);

		for (int i = 0; i < threadCount; i++) {
			executor.submit(() -> {
				try {
					readyPaymentUseCase.ready(command);
				} catch (Exception ignored) {
				} finally {
					latch.countDown();
				}
			});
		}

		latch.await();

		long count = paymentJpaRepository.findAll().stream()
			.filter(e -> e.getOrderId().equals(orderId))
			.count();

		assertEquals(1, count, "중복 결제가 생성되면 안됩니다.");
	}
}
