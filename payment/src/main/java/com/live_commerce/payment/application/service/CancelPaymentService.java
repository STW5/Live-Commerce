package com.live_commerce.payment.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.payment.application.exception.CustomException;
import com.live_commerce.payment.application.exception.PaymentExceptionCode;
import com.live_commerce.payment.application.port.in.CancelPaymentUseCase;
import com.live_commerce.payment.application.port.out.LoadPaymentPort;
import com.live_commerce.payment.application.port.out.SavePaymentPort;
import com.live_commerce.payment.domain.model.Payment;
import com.live_commerce.payment.domain.service.PaymentValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 취소 서비스
 * - 단일 책임: 결제 취소 유스케이스만 처리
 * - PENDING → CANCELED 상태 전이 (외부 결제 게이트웨이 호출 없음)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelPaymentService implements CancelPaymentUseCase {

	private final LoadPaymentPort loadPaymentPort;
	private final SavePaymentPort savePaymentPort;
	private final PaymentValidator paymentValidator;

	@Override
	@Transactional
	public void cancel(CancelPaymentCommand command) {
		Payment payment = loadPaymentPort.loadByOrderId(command.orderId())
			.orElseThrow(() -> new CustomException(PaymentExceptionCode.NOT_FOUND));

		// 권한 검증 (도메인 서비스 활용)
		paymentValidator.validateCancelPermission(
			payment,
			command.userId(),
			command.hasMasterRole()
		);

		// 도메인 규칙 검증: PENDING 상태만 취소 가능
		if (!payment.isPending()) {
			throw new CustomException(PaymentExceptionCode.INVALID_STATUS);
		}

		payment.cancel();
		savePaymentPort.save(payment);

		log.info("[Payment] 결제 취소 완료 - orderId: {}", command.orderId());
	}
}
