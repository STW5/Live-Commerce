package com.live_commerce.payment.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import com.live_commerce.payment.application.port.KakaoPayClient;
import com.live_commerce.payment.domain.model.Payment;
import com.live_commerce.payment.domain.model.PaymentStatus;
import com.live_commerce.payment.domain.repository.PaymentRepository;
import com.live_commerce.payment.infrastructure.client.OrderClient;
import com.live_commerce.payment.infrastructure.security.RequestUserDetails;

/**
 * PaymentService 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PaymentServiceTest {

	@Autowired
	private PaymentService paymentService;
	@Autowired
	private PaymentRepository paymentRepository;
	@Autowired
	private RedissonClient redissonClient;

	@MockitoBean
	private OrderClient orderClient;
	@MockitoBean
	private KakaoPayClient kakaoPayClient;

	private UUID userId;
	private UUID orderId;

	@BeforeEach
	void setup() {
		paymentRepository.deleteAll();
		userId = UUID.randomUUID();
		orderId = UUID.randomUUID();
	}

	@DisplayName("결제 단건 조회 - 소유자")
	@Test
	void getPayment_byOwner_success() {
		// Given
		Payment payment = Payment.of(userId, orderId, BigDecimal.valueOf(7000));
		paymentRepository.save(payment);
		RequestUserDetails userDetails = new RequestUserDetails(userId, null, Collections.emptyList());
		// When
		PaymentGetResponseDto result = paymentService.getPayment(payment.getId(), userDetails);
		// Then
		assertEquals(payment.getId(), result.paymentId());
		assertEquals(orderId, result.orderId());
	}

	@DisplayName("결제 단건 조회 - 마스터")
	@Test
	void getPayment_byMaster_success() {
		// Given
		Payment payment = Payment.of(userId, orderId, BigDecimal.valueOf(7000));
		paymentRepository.save(payment);
		RequestUserDetails master = new RequestUserDetails(UUID.randomUUID(), null, List.of(() -> "ROLE_MASTER"));
		// When
		PaymentGetResponseDto result = paymentService.getPayment(payment.getId(), master);
		// Then
		assertEquals(payment.getId(), result.paymentId());
	}

	@DisplayName("결제 단건 조회 실패 - 권한 없음")
	@Test
	void getPayment_byUnauthorizedUser_fail() {
		// Given
		Payment payment = Payment.of(userId, orderId, BigDecimal.valueOf(5000));
		paymentRepository.save(payment);
		RequestUserDetails otherUser = new RequestUserDetails(UUID.randomUUID(), null, Collections.emptyList());
		// When & Then
		CustomException ex = assertThrows(CustomException.class, () -> {
			paymentService.getPayment(payment.getId(), otherUser);
		});
		assertEquals(PaymentExceptionCode.UNAUTHORIZED, ex.getExceptionCode());
	}

	@DisplayName("결제 전체 조회 - 소유자")
	@Test
	void getPayments_byOwner_paginated_success() {
		// Given
		for (int i = 0; i < 12; i++) {
			paymentRepository.save(Payment.of(userId, UUID.randomUUID(), BigDecimal.valueOf(1000 + i)));
		}
		RequestUserDetails userDetails = new RequestUserDetails(userId, null, Collections.emptyList());
		PageRequest pageable = PageRequest.of(0, 10);
		// When
		Page<PaymentGetResponseDto> result = paymentService.getPayments(
			new PaymentSearchCondition(null, null, null, null, null),
			userDetails,
			pageable
		);
		// Then
		assertEquals(10, result.getContent().size());
		assertEquals(12, result.getTotalElements());
	}

	@DisplayName("결제 전체 조회 - 마스터")
	@Test
	void getPayments_byMaster_all_success() {
		// Given
		for (int i = 0; i < 5; i++) {
			paymentRepository.save(Payment.of(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(1000 + i)));
		}
		RequestUserDetails master = new RequestUserDetails(UUID.randomUUID(), null, List.of(() -> "ROLE_MASTER"));
		PageRequest pageable = PageRequest.of(0, 10);
		// When
		Page<PaymentGetResponseDto> result = paymentService.getPayments(
			new PaymentSearchCondition(null, null, null, null, null),
			master,
			pageable
		);
		// Then
		assertEquals(5, result.getContent().size());
	}

	@DisplayName("결제 취소 - 상태가 PENDING일 경우 성공")
	@Test
	void cancelPayment_pending_success() {
		// Given
		Payment payment = Payment.of(userId, orderId, BigDecimal.valueOf(8000));
		paymentRepository.save(payment);
		RequestUserDetails userDetails = new RequestUserDetails(userId, null, Collections.emptyList());
		// When
		paymentService.cancelPaymentByOrderId(orderId, userDetails);
		// Then
		Payment updated = paymentRepository.findByOrderId(orderId).orElseThrow();
		assertEquals(PaymentStatus.CANCELED, updated.getStatus());
	}

	@DisplayName("결제 취소 - 마스터가 다른 유저 결제 취소")
	@Test
	void cancelPayment_byMaster_success() {
		// Given
		Payment payment = Payment.of(userId, orderId, BigDecimal.valueOf(8888));
		paymentRepository.save(payment);
		RequestUserDetails master = new RequestUserDetails(UUID.randomUUID(), null, List.of(() -> "ROLE_MASTER"));
		// When
		paymentService.cancelPaymentByOrderId(orderId, master);
		// Then
		Payment updated = paymentRepository.findByOrderId(orderId).orElseThrow();
		assertEquals(PaymentStatus.CANCELED, updated.getStatus());
	}

	@DisplayName("결제 취소 실패 - 이미 COMPLETED 상태")
	@Test
	void cancelPayment_alreadyCompleted_fail() {
		// Given
		Payment payment = Payment.of(userId, orderId, BigDecimal.valueOf(15000));
		payment.complete();
		paymentRepository.save(payment);
		RequestUserDetails userDetails = new RequestUserDetails(userId, null, Collections.emptyList());
		// When & Then
		CustomException ex = assertThrows(CustomException.class, () -> {
			paymentService.cancelPaymentByOrderId(orderId, userDetails);
		});
		assertEquals(PaymentExceptionCode.INVALID_STATUS, ex.getExceptionCode());
	}

	@DisplayName("결제 취소 실패 - 소유자도 마스터도 아님")
	@Test
	void cancelPayment_unauthorizedUser_fail() {
		// Given
		Payment payment = Payment.of(userId, orderId, BigDecimal.valueOf(5000));
		paymentRepository.save(payment);
		RequestUserDetails otherUser = new RequestUserDetails(UUID.randomUUID(), null, Collections.emptyList());
		// When & Then
		CustomException ex = assertThrows(CustomException.class, () -> {
			paymentService.cancelPaymentByOrderId(orderId, otherUser);
		});
		assertEquals(PaymentExceptionCode.UNAUTHORIZED, ex.getExceptionCode());
	}

	@DisplayName("결제 환불 - COMPLETED 상태에서 성공")
	@Test
	void refundPayment_completed_success() {
		// Given
		Payment payment = Payment.of(userId, orderId, BigDecimal.valueOf(15000));
		payment.complete();
		payment.assignTid("TID123");
		paymentRepository.save(payment);
		RequestUserDetails userDetails = new RequestUserDetails(userId, null, Collections.emptyList());
		when(kakaoPayClient.requestKakaoPayCancel(any(), any())).thenReturn(null);
		// When
		PaymentRefundResponseDto response = paymentService.refundPaymentByOrderId(orderId, userDetails);
		// Then
		assertEquals(PaymentStatus.REFUND, paymentRepository.findByOrderId(orderId).get().getStatus());
		assertEquals(orderId, response.orderId());
	}

	@DisplayName("결제 환불 - 마스터가 다른 유저 결제 환불")
	@Test
	void refundPayment_byMaster_success() {
		// Given
		Payment payment = Payment.of(userId, orderId, BigDecimal.valueOf(20000));
		payment.complete();
		payment.assignTid("TID999");
		paymentRepository.save(payment);
		RequestUserDetails master = new RequestUserDetails(UUID.randomUUID(), null, List.of(() -> "ROLE_MASTER"));
		when(kakaoPayClient.requestKakaoPayCancel(any(), any())).thenReturn(null);
		// When
		PaymentRefundResponseDto result = paymentService.refundPaymentByOrderId(orderId, master);
		// Then
		assertEquals(PaymentStatus.REFUND, paymentRepository.findByOrderId(orderId).get().getStatus());
		assertEquals(orderId, result.orderId());
	}

	@DisplayName("결제 환불 실패 - 상태가 COMPLETED가 아님")
	@Test
	void refundPayment_notCompleted_fail() {
		// Given
		Payment payment = Payment.of(userId, orderId, BigDecimal.valueOf(15000));
		// PENDING은 기본 상태이므로 변경 불필요 (이미 PENDING)
		paymentRepository.save(payment);
		RequestUserDetails userDetails = new RequestUserDetails(userId, null, Collections.emptyList());
		// When & Then
		CustomException ex = assertThrows(CustomException.class, () -> {
			paymentService.refundPaymentByOrderId(orderId, userDetails);
		});
		assertEquals(PaymentExceptionCode.INVALID_STATUS, ex.getExceptionCode());
	}

	@DisplayName("결제 환불 실패 - 소유자도 마스터도 아님")
	@Test
	void refundPayment_unauthorizedUser_fail() {
		// Given
		Payment payment = Payment.of(userId, orderId, BigDecimal.valueOf(9999));
		payment.complete();
		payment.assignTid("TID456");
		paymentRepository.save(payment);
		RequestUserDetails otherUser = new RequestUserDetails(UUID.randomUUID(), null, Collections.emptyList());
		// When & Then
		CustomException ex = assertThrows(CustomException.class, () -> {
			paymentService.refundPaymentByOrderId(orderId, otherUser);
		});
		assertEquals(PaymentExceptionCode.UNAUTHORIZED, ex.getExceptionCode());
	}
}
