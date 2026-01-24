package com.live_commerce.payment.application.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.payment.application.dto.request.PaymentApproveRequestDto;
import com.live_commerce.payment.application.dto.request.PaymentReadyRequestDto;
import com.live_commerce.payment.application.dto.request.PaymentRefundResponseDto;
import com.live_commerce.payment.application.dto.request.PaymentSearchCondition;
import com.live_commerce.payment.application.dto.response.PaymentApproveResponseDto;
import com.live_commerce.payment.application.dto.response.PaymentGetResponseDto;
import com.live_commerce.payment.application.dto.response.PaymentReadyResponseDto;
import com.live_commerce.payment.application.exception.CustomException;
import com.live_commerce.payment.application.exception.KakaoPayApiException;
import com.live_commerce.payment.application.exception.PaymentExceptionCode;
import com.live_commerce.payment.application.port.KakaoPayClient;
import com.live_commerce.payment.domain.model.Payment;
import com.live_commerce.payment.domain.model.PaymentStatus;
import com.live_commerce.payment.domain.repository.PaymentRepository;
import com.live_commerce.payment.infrastructure.client.OrderClient;
import com.live_commerce.payment.infrastructure.client.dto.KakaoPayApproveDto;
import com.live_commerce.payment.infrastructure.client.dto.KakaoPayReadyDto;
import com.live_commerce.payment.infrastructure.client.dto.PaymentCancelRequest;
import com.live_commerce.payment.infrastructure.kafka.event.PaymentCompletedEvent;
import com.live_commerce.payment.infrastructure.kafka.event.PaymentFailedEvent;
import com.live_commerce.payment.infrastructure.kafka.producer.PaymentEventProducer;
import com.live_commerce.payment.infrastructure.lock.DistributedLock;
import com.live_commerce.payment.infrastructure.security.RequestUserDetails;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceV2 {

	private final PaymentRepository paymentRepository;
	private final KakaoPayClient kakaoPayClient;
	private final OrderClient orderClient;
	private final RedissonClient redissonClient;
	private final PaymentEventProducer paymentEventProducer;

	@DistributedLock(key = "#dto.orderId")
	@Transactional
	public PaymentReadyResponseDto readyPayment(RequestUserDetails user, PaymentReadyRequestDto dto) {
		paymentRepository.findByOrderId(dto.orderId()).ifPresent(existing -> {
			if (existing.getStatus() != PaymentStatus.FAILED) {
				throw new CustomException(PaymentExceptionCode.DUPLICATE_PAYMENT);
			}
		});

		KakaoPayReadyDto readyDto = kakaoPayClient.requestKakaoPayReady(
			user.getUserId(), dto.orderId(), dto.amount(), dto.itemName()
		);

		Payment payment = dto.toEntity(user.getUserId());
		payment.assignTid(readyDto.tid());
		paymentRepository.save(payment);

		String key = "payment:expire:" + dto.orderId();
		RBucket<String> bucket = redissonClient.getBucket(key);
		bucket.set(payment.getId().toString(), 10, TimeUnit.MINUTES);

		return PaymentReadyResponseDto.from(readyDto);
	}

	@Transactional
	public PaymentApproveResponseDto approvePayment(PaymentApproveRequestDto requestDto, UUID userId) {
		Payment payment = paymentRepository.findByOrderId(UUID.fromString(requestDto.orderId()))
			.orElseThrow(() -> new CustomException(PaymentExceptionCode.NOT_FOUND));

		// 상태 검증 (상태 변경 전에 예외 발생)
		if (payment.getStatus() != PaymentStatus.PENDING) {
			throw new CustomException(PaymentExceptionCode.INVALID_STATUS);
		}

		// 1. 외부 API 호출 (실패 시 예외 발생)
		KakaoPayApproveDto approveDto;
		try {
			approveDto = kakaoPayClient.requestKakaoPayApprove(
				requestDto.tid(), requestDto.pgToken(), requestDto.orderId(), userId.toString()
			);
		} catch (KakaoPayApiException e) {
			log.warn("[Payment] 카카오페이 승인 실패 - orderId: {}, 사유: {}", requestDto.orderId(), e.getMessage());

			// 2-1. 실패 시: 도메인 메서드 사용
			payment.failWithReason("카카오페이 승인 실패: " + e.getMessage());
			paymentEventProducer.sendPaymentFailed(
				new PaymentFailedEvent(payment.getOrderId(), "카카오페이 승인 실패: " + e.getMessage())
			);

			throw new CustomException(PaymentExceptionCode.PAYMENT_APPROVE_FAIL);
		}

		// 2-2. 성공 시: 도메인 메서드 사용
		payment.complete();
		paymentEventProducer.sendPaymentCompleted(
			new PaymentCompletedEvent(
				payment.getOrderId(),
				"결제 완료",
				payment.getAmount()
			)
		);

		log.info("[Payment] 결제 승인 완료 - orderId: {}, amount: {}", payment.getOrderId(), payment.getAmount());

		return PaymentApproveResponseDto.from(approveDto);
	}

	@Transactional(readOnly = true)
	public PaymentGetResponseDto getPayment(UUID paymentId, RequestUserDetails userDetails) {
		Payment payment = findPaymentById(paymentId);
		validatePaymentGetPermission(payment, userDetails);

		return PaymentGetResponseDto.from(payment);
	}

	@Transactional(readOnly = true)
	public Page<PaymentGetResponseDto> getPayments(
		PaymentSearchCondition condition,
		RequestUserDetails userDetails,
		Pageable pageable
	) {
		validatePaymentSearchPermission(userDetails);

		int size = pageable.getPageSize();
		if (size != 10 && size != 30 && size != 50) {
			pageable = PageRequest.of(pageable.getPageNumber(), 10, pageable.getSort());
		}

		PaymentSearchCondition finalCondition = hasMasterRole(userDetails) ? condition :
			new PaymentSearchCondition(
				userDetails.getUserId(),
				condition.orderId(),
				condition.status(),
				condition.createdAtFrom(),
				condition.createdAtTo()
			);

		List<Payment> payments = paymentRepository.searchPayment(finalCondition, pageable);
		long totalCount = paymentRepository.countPayment(finalCondition);

		List<PaymentGetResponseDto> dtoList = payments.stream()
			.map(PaymentGetResponseDto::from)
			.toList();

		return new PageImpl<>(dtoList, pageable, totalCount);
	}

	@Transactional
	public PaymentRefundResponseDto refundPaymentByOrderId(UUID orderId, RequestUserDetails userDetails) {
		Payment payment = findPaymentByOrderId(orderId);
		validatePaymentRefundPermission(payment, userDetails);

		if (payment.getStatus() != PaymentStatus.COMPLETED) {
			throw new CustomException(PaymentExceptionCode.INVALID_STATUS);
		}

		// 1. 카카오페이 환불 처리
		try {
			kakaoPayClient.requestKakaoPayCancel(payment.getTid(), payment.getAmount());
		} catch (KakaoPayApiException e) {
			log.error("[Payment] 카카오페이 환불 실패 - orderId: {}, 사유: {}", orderId, e.getMessage());
			throw new CustomException(PaymentExceptionCode.PAYMENT_APPROVE_FAIL);
		}

		// 2. 도메인 메서드 사용
		payment.refund();

		// 3. 주문 서비스 통지 (비동기, 실패해도 환불은 완료됨)
		try {
			orderClient.notifyOrderCancel(orderId, new PaymentCancelRequest(false, "결제 취소 처리됨"));
			log.info("[Payment] 주문 서비스에 환불 통지 완료 - orderId: {}", orderId);
		} catch (Exception e) {
			log.warn("[Payment] 주문 서비스 통지 실패 (환불은 완료됨) - orderId: {}, 사유: {}", orderId, e.getMessage());
		}

		return PaymentRefundResponseDto.from(payment);
	}

	@Transactional
	public void cancelPaymentByOrderId(UUID orderId, RequestUserDetails userDetails) {
		Payment payment = findPaymentByOrderId(orderId);
		validatePaymentCancelPermission(payment, userDetails);

		if (payment.getStatus() != PaymentStatus.PENDING) {
			throw new CustomException(PaymentExceptionCode.INVALID_STATUS);
		}

		// 1. 도메인 메서드 사용 (PENDING -> CANCELED)
		payment.cancel();

		// 2. 주문 서비스 통지 (실패해도 취소는 완료됨)
		try {
			orderClient.notifyOrderCancel(orderId, new PaymentCancelRequest(false, "결제 취소 처리됨"));
			log.info("[Payment] 주문 서비스에 취소 통지 완료 - orderId: {}", orderId);
		} catch (Exception e) {
			log.warn("[Payment] 주문 서비스 통지 실패 (취소는 완료됨) - orderId: {}, 사유: {}", orderId, e.getMessage());
		}
	}

	@Transactional
	public void compensateRefundByOrderId(UUID orderId, String message) {
		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new CustomException(PaymentExceptionCode.NOT_FOUND));

		// 이미 환불/취소된 경우 스킵
		if (payment.getStatus() != PaymentStatus.COMPLETED) {
			log.info("[Payment] 보상 처리 스킵: 이미 취소/실패한 결제입니다. orderId={}, status={}", orderId, payment.getStatus());
			return;
		}

		// 1. 카카오페이 환불 처리
		try {
			kakaoPayClient.requestKakaoPayCancel(payment.getTid(), payment.getAmount());
		} catch (KakaoPayApiException e) {
			log.error("[Payment] 보상 환불 실패 - orderId: {}, 사유: {}", orderId, e.getMessage());
			// 보상 트랜잭션이므로 재시도 필요 (수동 개입 알림 필요)
			throw e;
		}

		// 2. 도메인 메서드 사용
		payment.refund();

		log.info("[Payment] 보상 결제 취소 완료: orderId = {}, message = {}", orderId, message);
	}


	private Payment findPaymentById(UUID paymentId) {
		return paymentRepository.findById(paymentId)
			.orElseThrow(() -> new CustomException(PaymentExceptionCode.NOT_FOUND));
	}

	private Payment findPaymentByOrderId(UUID orderId) {
		return paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new CustomException(PaymentExceptionCode.NOT_FOUND));
	}

	private void validatePaymentGetPermission(Payment payment, RequestUserDetails userDetails) {
		if (!payment.getUserId().equals(userDetails.getUserId()) && !hasMasterRole(userDetails)) {
			throw new CustomException(PaymentExceptionCode.UNAUTHORIZED);
		}
	}

	private void validatePaymentSearchPermission(RequestUserDetails userDetails) {
		if (!isSelf(userDetails.getUserId(), userDetails) && !hasMasterRole(userDetails)) {
			throw new CustomException(PaymentExceptionCode.UNAUTHORIZED);
		}
	}

	private void validatePaymentRefundPermission(Payment payment, RequestUserDetails userDetails) {
		if (!payment.getUserId().equals(userDetails.getUserId()) && !hasMasterRole(userDetails)) {
			throw new CustomException(PaymentExceptionCode.UNAUTHORIZED);
		}
	}

	private void validatePaymentCancelPermission(Payment payment, RequestUserDetails userDetails) {
		if (!payment.getUserId().equals(userDetails.getUserId()) && !hasMasterRole(userDetails)) {
			throw new CustomException(PaymentExceptionCode.UNAUTHORIZED);
		}
	}

	private boolean isSelf(UUID userId, RequestUserDetails userDetails) {
		return userId.equals(userDetails.getUserId());
	}

	private boolean hasMasterRole(RequestUserDetails userDetails) {
		return userDetails.getAuthorities().stream()
			.anyMatch(auth -> auth.getAuthority().equals("ROLE_MASTER"));
	}
}
