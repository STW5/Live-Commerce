package com.live_commerce.payment.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
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
public class PaymentReattemptTest {

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

	private RLock mockLock;

	@BeforeEach
	void setUp() {
		mockLock = mock(RLock.class);
		try {
			when(mockLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		when(mockLock.isHeldByCurrentThread()).thenReturn(true);
		when(redissonClient.getLock(anyString())).thenReturn(mockLock);
	}

	@DisplayName("API 실패 후 동일 orderId로 재요청 시 결제 준비 성공")
	@Test
	void failedPayment_should_allow_repayment_on_same_orderId() {
		UUID userId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();
		ReadyPaymentCommand command = new ReadyPaymentCommand(userId, orderId, BigDecimal.valueOf(12000), "재결제 테스트");

		when(paymentGatewayPort.ready(any(), any(), any(), any()))
			.thenThrow(new RuntimeException("카카오페이 실패"));

		try {
			readyPaymentUseCase.ready(command);
		} catch (Exception ignored) {}

		assertTrue(paymentJpaRepository.findByOrderId(orderId).isEmpty());

		doReturn(new PaymentReadyResult("T_OK", "https://ok", "2025-04-19T00:00:00"))
			.when(paymentGatewayPort).ready(any(), any(), any(), any());

		assertDoesNotThrow(() -> readyPaymentUseCase.ready(command));

		Payment saved = paymentJpaRepository.findByOrderId(orderId)
			.map(PaymentJpaEntity::toDomain).orElseThrow();
		assertEquals("PENDING", saved.getStatus().name());
		assertEquals("T_OK", saved.getTid());
	}
}
