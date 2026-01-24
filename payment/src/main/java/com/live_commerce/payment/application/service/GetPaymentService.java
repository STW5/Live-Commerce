package com.live_commerce.payment.application.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.payment.application.dto.request.PaymentSearchCondition;
import com.live_commerce.payment.application.dto.response.PaymentGetResponseDto;
import com.live_commerce.payment.application.exception.CustomException;
import com.live_commerce.payment.application.exception.PaymentExceptionCode;
import com.live_commerce.payment.application.port.in.GetPaymentUseCase;
import com.live_commerce.payment.application.port.out.LoadPaymentPort;
import com.live_commerce.payment.domain.model.Payment;
import com.live_commerce.payment.domain.service.PaymentValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 조회 서비스
 * - 단일 책임: 결제 조회 유스케이스만 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetPaymentService implements GetPaymentUseCase {

	private static final List<Integer> ALLOWED_PAGE_SIZES = List.of(10, 30, 50);
	private static final int DEFAULT_PAGE_SIZE = 10;

	private final LoadPaymentPort loadPaymentPort;
	private final PaymentValidator paymentValidator;

	@Override
	@Transactional(readOnly = true)
	public PaymentGetResponseDto getById(GetPaymentQuery query) {
		Payment payment = loadPaymentPort.loadById(query.paymentId())
			.orElseThrow(() -> new CustomException(PaymentExceptionCode.NOT_FOUND));

		// 권한 검증
		paymentValidator.validateGetPermission(
			payment,
			query.userId(),
			query.hasMasterRole()
		);

		return PaymentGetResponseDto.from(payment);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<PaymentGetResponseDto> search(SearchPaymentQuery query, Pageable pageable) {
		// 검색 권한 검증
		paymentValidator.validateSearchPermission(
			query.condition().userId(),
			query.requestUserId(),
			query.hasMasterRole()
		);

		// 페이지 크기 검증 및 정규화
		pageable = normalizePageable(pageable);

		// 검색 조건 결정 (마스터가 아니면 본인 것만 조회)
		PaymentSearchCondition finalCondition = determineSearchCondition(query);

		// 조회 실행
		List<Payment> payments = loadPaymentPort.search(finalCondition, pageable);
		long totalCount = loadPaymentPort.count(finalCondition);

		List<PaymentGetResponseDto> dtoList = payments.stream()
			.map(PaymentGetResponseDto::from)
			.toList();

		return new PageImpl<>(dtoList, pageable, totalCount);
	}

	private Pageable normalizePageable(Pageable pageable) {
		int size = pageable.getPageSize();
		if (!ALLOWED_PAGE_SIZES.contains(size)) {
			return PageRequest.of(pageable.getPageNumber(), DEFAULT_PAGE_SIZE, pageable.getSort());
		}
		return pageable;
	}

	private PaymentSearchCondition determineSearchCondition(SearchPaymentQuery query) {
		if (query.hasMasterRole()) {
			return query.condition();
		}

		// 일반 사용자는 본인 것만 조회
		return new PaymentSearchCondition(
			query.requestUserId(),
			query.condition().orderId(),
			query.condition().status(),
			query.condition().createdAtFrom(),
			query.condition().createdAtTo()
		);
	}
}
