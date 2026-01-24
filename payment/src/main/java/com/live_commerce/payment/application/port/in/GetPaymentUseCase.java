package com.live_commerce.payment.application.port.in;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.live_commerce.payment.application.dto.request.PaymentSearchCondition;
import com.live_commerce.payment.application.dto.response.PaymentGetResponseDto;

/**
 * 결제 조회 유스케이스
 */
public interface GetPaymentUseCase {
	PaymentGetResponseDto getById(GetPaymentQuery query);

	Page<PaymentGetResponseDto> search(SearchPaymentQuery query, Pageable pageable);

	/**
	 * 결제 단건 조회 쿼리
	 */
	record GetPaymentQuery(
		UUID paymentId,
		UUID userId,
		boolean hasMasterRole
	) {
		public GetPaymentQuery {
			if (paymentId == null || userId == null) {
				throw new IllegalArgumentException("paymentId와 userId는 필수입니다");
			}
		}
	}

	/**
	 * 결제 검색 쿼리
	 */
	record SearchPaymentQuery(
		PaymentSearchCondition condition,
		UUID requestUserId,
		boolean hasMasterRole
	) {
		public SearchPaymentQuery {
			if (condition == null || requestUserId == null) {
				throw new IllegalArgumentException("condition과 requestUserId는 필수입니다");
			}
		}
	}
}
