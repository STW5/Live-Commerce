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
import com.live_commerce.payment.application.exception.PaymentExceptionCode;
import com.live_commerce.payment.application.port.KakaoPayClient;
import com.live_commerce.payment.domain.model.Payment;
import com.live_commerce.payment.domain.model.PaymentStatus;
import com.live_commerce.payment.domain.repository.PaymentRepository;
import com.live_commerce.payment.infrastructure.client.OrderClient;
import com.live_commerce.payment.infrastructure.client.dto.KakaoPayApproveDto;
import com.live_commerce.payment.infrastructure.client.dto.KakaoPayReadyDto;
import com.live_commerce.payment.infrastructure.client.dto.PaymentCancelRequest;
import com.live_commerce.payment.infrastructure.client.dto.PaymentFailRequest;
import com.live_commerce.payment.infrastructure.client.dto.PaymentSuccessRequest;
import com.live_commerce.payment.infrastructure.lock.DistributedLock;
import com.live_commerce.payment.infrastructure.security.RequestUserDetails;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

	private final PaymentRepository paymentRepository;
	private final KakaoPayClient kakaoPayClient;
	private final OrderClient orderClient;

	private final RedissonClient redissonClient;

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

		// 1) 승인 가능한 상태인지 체크
		if (payment.getStatus() != PaymentStatus.PENDING) {
			payment.fail(); // 승인 전 상태가 아니면 실패 처리
			throw new CustomException(PaymentExceptionCode.INVALID_STATUS);
		}

		// 2) 승인 요청 시도
		KakaoPayApproveDto approveDto;
		try {
			approveDto = kakaoPayClient.requestKakaoPayApprove(
				requestDto.tid(),
				requestDto.pgToken(),
				requestDto.orderId(),
				userId.toString()
			);
		} catch (Exception e) {
			// 카카오 API 호출 자체가 실패한 경우
			payment.fail();


			// 주문 서비스에 결제 실패 알림
			try {
				orderClient.notifyPaymentFail(
					payment.getOrderId(),
					new PaymentFailRequest(false, "카카오페이 승인 실패")
				);
			} catch (Exception ex) {
				log.warn("주문 서비스에 결제 실패 알림 전송 실패: {}", ex.getMessage());
			}


			throw new CustomException(PaymentExceptionCode.PAYMENT_APPROVE_FAIL);
		}

		// 3) 성공적으로 승인됐으면 도메인 메서드 사용
		payment.complete();

		// 주문 서비스에 결제 성공 알림
		try {
			PaymentSuccessRequest request = new PaymentSuccessRequest(
				true,
				"결제 처리 완료",
				payment.getAmount()
			);

			orderClient.notifyPaymentSuccess(payment.getOrderId(), request);
		} catch (Exception e) {
			log.warn("주문 서비스에 결제 성공 알림 실패: {}", e.getMessage());
		}

		// 4) 응답 DTO 반환
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

		// 페이지 사이즈 검증
		int size = pageable.getPageSize();
		if (size != 10 && size != 30 && size != 50) {
			pageable = PageRequest.of(pageable.getPageNumber(), 10, pageable.getSort());
		}

		// 마스터 권한이 아니라면 userId 강제 세팅
		PaymentSearchCondition finalCondition;
		if (hasMasterRole(userDetails)) {
			// 마스터면 원본 condition 그대로 사용
			finalCondition = condition;
		} else {
			// 일반 유저면 userId만 자기 것으로 덮어씌운다
			finalCondition = new PaymentSearchCondition(
				userDetails.getUserId(),
				condition.orderId(),
				condition.status(),
				condition.createdAtFrom(),
				condition.createdAtTo()
			);
		}

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

		kakaoPayClient.requestKakaoPayCancel(payment.getTid(), payment.getAmount());
		payment.refund();

		try {
			orderClient.notifyOrderCancel(
				orderId,
				new PaymentCancelRequest(false, "결제 취소 처리됨")
			);
		} catch (Exception e) {
			log.warn("주문 서비스에 결제 취소 알림 실패: {}", e.getMessage());
		}


		return PaymentRefundResponseDto.from(payment);
	}


	@Transactional
	public void cancelPaymentByOrderId(UUID orderId, RequestUserDetails userDetails) {
		Payment payment = findPaymentByOrderId(orderId);
		validatePaymentCancelPermission(payment, userDetails);

		// 상태 확인: 아직 결제가 승인되지 않은 상태여야 함
		if (payment.getStatus() != PaymentStatus.PENDING) {
			throw new CustomException(PaymentExceptionCode.INVALID_STATUS);
		}

		try {
			orderClient.notifyOrderCancel(
				orderId,
				new PaymentCancelRequest(false, "결제 취소 처리됨")
			);
		} catch (Exception e) {
			log.warn("주문 서비스에 결제 취소 알림 실패: {}", e.getMessage());
		}

		payment.cancel();
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

	private Payment findPaymentById(UUID paymentId) {
		return paymentRepository.findById(paymentId)
			.orElseThrow(() -> new CustomException(PaymentExceptionCode.NOT_FOUND));
	}

	private Payment findPaymentByOrderId(UUID orderId) {
		return paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new CustomException(PaymentExceptionCode.NOT_FOUND));
	}


}