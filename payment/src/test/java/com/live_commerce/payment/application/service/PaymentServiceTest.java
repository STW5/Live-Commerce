package com.live_commerce.payment.application.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.payment.application.dto.request.PaymentRefundResponseDto;
import com.live_commerce.payment.application.dto.request.PaymentSearchCondition;
import com.live_commerce.payment.application.dto.response.PaymentGetResponseDto;
import com.live_commerce.payment.application.exception.CustomException;
import com.live_commerce.payment.application.exception.PaymentExceptionCode;
import com.live_commerce.payment.application.port.in.CancelPaymentUseCase;
import com.live_commerce.payment.application.port.in.CancelPaymentUseCase.CancelPaymentCommand;
import com.live_commerce.payment.application.port.in.GetPaymentUseCase;
import com.live_commerce.payment.application.port.in.GetPaymentUseCase.GetPaymentQuery;
import com.live_commerce.payment.application.port.in.GetPaymentUseCase.SearchPaymentQuery;
import com.live_commerce.payment.application.port.in.RefundPaymentUseCase;
import com.live_commerce.payment.application.port.in.RefundPaymentUseCase.RefundPaymentCommand;
import com.live_commerce.payment.application.port.out.ManagePaymentExpirationPort;
import com.live_commerce.payment.application.port.out.PaymentGatewayPort;
import com.live_commerce.payment.domain.model.Payment;
import com.live_commerce.payment.domain.model.PaymentStatus;
import com.live_commerce.payment.infrastructure.adapter.persistence.PaymentJpaEntity;
import com.live_commerce.payment.infrastructure.adapter.persistence.PaymentJpaRepository;

/**
 * 결제 핵사고날 UseCase 통합 테스트
 * - GetPaymentUseCase, RefundPaymentUseCase, CancelPaymentUseCase 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PaymentServiceTest {

	@Autowired
	private GetPaymentUseCase getPaymentUseCase;
	@Autowired
	private RefundPaymentUseCase refundPaymentUseCase;
	@Autowired
	private CancelPaymentUseCase cancelPaymentUseCase;
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

	private UUID userId;
	private UUID orderId;

	@BeforeEach
	void setup() {
		paymentJpaRepository.deleteAll();
		userId = UUID.randomUUID();
		orderId = UUID.randomUUID();
	}

	private Payment savePayment(Payment domain) {
		return paymentJpaRepository.save(PaymentJpaEntity.from(domain)).toDomain();
	}

	// ── GetPayment ────────────────────────────────────────────────────────────

	@DisplayName("결제 단건 조회 - 소유자")
	@Test
	void getPayment_byOwner_success() {
		Payment payment = savePayment(Payment.of(userId, orderId, BigDecimal.valueOf(7000)));
		PaymentGetResponseDto result = getPaymentUseCase.getById(
			new GetPaymentQuery(payment.getId(), userId, false)
		);
		assertEquals(payment.getId(), result.paymentId());
		assertEquals(orderId, result.orderId());
	}

	@DisplayName("결제 단건 조회 - 마스터")
	@Test
	void getPayment_byMaster_success() {
		Payment payment = savePayment(Payment.of(userId, orderId, BigDecimal.valueOf(7000)));
		UUID masterId = UUID.randomUUID();
		PaymentGetResponseDto result = getPaymentUseCase.getById(
			new GetPaymentQuery(payment.getId(), masterId, true)
		);
		assertEquals(payment.getId(), result.paymentId());
	}

	@DisplayName("결제 단건 조회 실패 - 권한 없음")
	@Test
	void getPayment_byUnauthorizedUser_fail() {
		Payment payment = savePayment(Payment.of(userId, orderId, BigDecimal.valueOf(5000)));
		UUID otherId = UUID.randomUUID();
		assertThrows(IllegalAccessError.class, () ->
			getPaymentUseCase.getById(new GetPaymentQuery(payment.getId(), otherId, false))
		);
	}

	@DisplayName("결제 전체 조회 - 소유자 페이지네이션")
	@Test
	void getPayments_byOwner_paginated_success() {
		for (int i = 0; i < 12; i++) {
			savePayment(Payment.of(userId, UUID.randomUUID(), BigDecimal.valueOf(1000 + i)));
		}
		// 소유자 검색: condition.userId에 본인 ID 지정
		Page<PaymentGetResponseDto> result = getPaymentUseCase.search(
			new SearchPaymentQuery(new PaymentSearchCondition(userId, null, null, null, null), userId, false),
			PageRequest.of(0, 10)
		);
		assertEquals(10, result.getContent().size());
		assertEquals(12, result.getTotalElements());
	}

	@DisplayName("결제 전체 조회 - 마스터 전체 조회")
	@Test
	void getPayments_byMaster_all_success() {
		for (int i = 0; i < 5; i++) {
			savePayment(Payment.of(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(1000 + i)));
		}
		UUID masterId = UUID.randomUUID();
		Page<PaymentGetResponseDto> result = getPaymentUseCase.search(
			new SearchPaymentQuery(new PaymentSearchCondition(null, null, null, null, null), masterId, true),
			PageRequest.of(0, 10)
		);
		assertEquals(5, result.getContent().size());
	}

	// ── CancelPayment ─────────────────────────────────────────────────────────

	@DisplayName("결제 취소 - 상태가 PENDING일 경우 성공")
	@Test
	void cancelPayment_pending_success() {
		savePayment(Payment.of(userId, orderId, BigDecimal.valueOf(8000)));
		cancelPaymentUseCase.cancel(new CancelPaymentCommand(orderId, userId, false));
		Payment updated = paymentJpaRepository.findByOrderId(orderId)
			.map(PaymentJpaEntity::toDomain).orElseThrow();
		assertEquals(PaymentStatus.CANCELED, updated.getStatus());
	}

	@DisplayName("결제 취소 - 마스터가 다른 유저 결제 취소")
	@Test
	void cancelPayment_byMaster_success() {
		savePayment(Payment.of(userId, orderId, BigDecimal.valueOf(8888)));
		UUID masterId = UUID.randomUUID();
		cancelPaymentUseCase.cancel(new CancelPaymentCommand(orderId, masterId, true));
		Payment updated = paymentJpaRepository.findByOrderId(orderId)
			.map(PaymentJpaEntity::toDomain).orElseThrow();
		assertEquals(PaymentStatus.CANCELED, updated.getStatus());
	}

	@DisplayName("결제 취소 실패 - 이미 COMPLETED 상태")
	@Test
	void cancelPayment_alreadyCompleted_fail() {
		Payment domain = Payment.of(userId, orderId, BigDecimal.valueOf(15000));
		domain.complete();
		savePayment(domain);
		CustomException ex = assertThrows(CustomException.class, () ->
			cancelPaymentUseCase.cancel(new CancelPaymentCommand(orderId, userId, false))
		);
		assertEquals(PaymentExceptionCode.INVALID_STATUS, ex.getExceptionCode());
	}

	@DisplayName("결제 취소 실패 - 소유자도 마스터도 아님")
	@Test
	void cancelPayment_unauthorizedUser_fail() {
		savePayment(Payment.of(userId, orderId, BigDecimal.valueOf(5000)));
		UUID otherId = UUID.randomUUID();
		assertThrows(IllegalAccessError.class, () ->
			cancelPaymentUseCase.cancel(new CancelPaymentCommand(orderId, otherId, false))
		);
	}

	// ── RefundPayment ─────────────────────────────────────────────────────────

	@DisplayName("결제 환불 - COMPLETED 상태에서 성공")
	@Test
	void refundPayment_completed_success() {
		Payment domain = Payment.of(userId, orderId, BigDecimal.valueOf(15000));
		domain.complete();
		domain.assignTid("TID123");
		savePayment(domain);

		PaymentRefundResponseDto response = refundPaymentUseCase.refund(
			new RefundPaymentCommand(orderId, userId, false)
		);

		Payment updated = paymentJpaRepository.findByOrderId(orderId)
			.map(PaymentJpaEntity::toDomain).orElseThrow();
		assertEquals(PaymentStatus.REFUND, updated.getStatus());
		assertEquals(orderId, response.orderId());
	}

	@DisplayName("결제 환불 - 마스터가 다른 유저 결제 환불")
	@Test
	void refundPayment_byMaster_success() {
		Payment domain = Payment.of(userId, orderId, BigDecimal.valueOf(20000));
		domain.complete();
		domain.assignTid("TID999");
		savePayment(domain);
		UUID masterId = UUID.randomUUID();

		PaymentRefundResponseDto result = refundPaymentUseCase.refund(
			new RefundPaymentCommand(orderId, masterId, true)
		);

		Payment updated = paymentJpaRepository.findByOrderId(orderId)
			.map(PaymentJpaEntity::toDomain).orElseThrow();
		assertEquals(PaymentStatus.REFUND, updated.getStatus());
		assertEquals(orderId, result.orderId());
	}

	@DisplayName("결제 환불 실패 - 상태가 COMPLETED가 아님")
	@Test
	void refundPayment_notCompleted_fail() {
		savePayment(Payment.of(userId, orderId, BigDecimal.valueOf(15000)));
		CustomException ex = assertThrows(CustomException.class, () ->
			refundPaymentUseCase.refund(new RefundPaymentCommand(orderId, userId, false))
		);
		assertEquals(PaymentExceptionCode.INVALID_STATUS, ex.getExceptionCode());
	}

	@DisplayName("결제 환불 실패 - 소유자도 마스터도 아님")
	@Test
	void refundPayment_unauthorizedUser_fail() {
		Payment domain = Payment.of(userId, orderId, BigDecimal.valueOf(9999));
		domain.complete();
		domain.assignTid("TID456");
		savePayment(domain);
		UUID otherId = UUID.randomUUID();
		assertThrows(IllegalAccessError.class, () ->
			refundPaymentUseCase.refund(new RefundPaymentCommand(orderId, otherId, false))
		);
	}
}
