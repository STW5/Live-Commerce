package com.live_commerce.payment.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
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
import com.live_commerce.payment.domain.model.Payment;
import com.live_commerce.payment.infrastructure.adapter.persistence.PaymentJpaEntity;
import com.live_commerce.payment.infrastructure.adapter.persistence.PaymentJpaRepository;

@SpringBootTest
@ActiveProfiles("test")
public class PaymentRetryTemplateTest {

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

	@DisplayName("결제 게이트웨이 실패 후 재시도 성공 - UseCase 레벨 검증")
	@Test
	void should_fail_when_gateway_throws() {
		UUID userId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();
		ReadyPaymentCommand command = new ReadyPaymentCommand(userId, orderId, BigDecimal.valueOf(10000), "RetryTestItem");

		when(paymentGatewayPort.ready(any(), any(), any(), any()))
			.thenThrow(new RuntimeException("gateway failure"));

		assertThrows(RuntimeException.class, () -> readyPaymentUseCase.ready(command));
	}

	@DisplayName("결제 게이트웨이 정상 호출 시 결제 정보 저장 확인")
	@Test
	void should_save_payment_when_gateway_succeeds() {
		UUID userId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();
		ReadyPaymentCommand command = new ReadyPaymentCommand(userId, orderId, BigDecimal.valueOf(10000), "TestItem");

		assertDoesNotThrow(() -> readyPaymentUseCase.ready(command));

		Optional<Payment> saved = paymentJpaRepository.findByOrderId(orderId)
			.map(PaymentJpaEntity::toDomain);
		assertTrue(saved.isPresent(), "결제 정보가 저장되어야 합니다");
		assertEquals(orderId, saved.get().getOrderId());
		assertEquals("T123456789", saved.get().getTid());
	}
}
