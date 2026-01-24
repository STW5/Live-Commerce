package com.live_commerce.payment.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.payment.application.dto.request.PaymentRefundResponseDto;
import com.live_commerce.payment.application.exception.CustomException;
import com.live_commerce.payment.application.exception.KakaoPayApiException;
import com.live_commerce.payment.application.exception.PaymentExceptionCode;
import com.live_commerce.payment.application.port.in.RefundPaymentUseCase;
import com.live_commerce.payment.application.port.out.LoadPaymentPort;
import com.live_commerce.payment.application.port.out.PaymentGatewayPort;
import com.live_commerce.payment.application.port.out.SavePaymentPort;
import com.live_commerce.payment.domain.model.Payment;
import com.live_commerce.payment.domain.service.PaymentValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 환불 서비스
 * - 단일 책임: 결제 환불 유스케이스만 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundPaymentService implements RefundPaymentUseCase {

	private final LoadPaymentPort loadPaymentPort;
	private final SavePaymentPort savePaymentPort;
	private final PaymentGatewayPort paymentGatewayPort;
	private final PaymentValidator paymentValidator;

	@Override
	@Transactional
	public PaymentRefundResponseDto refund(RefundPaymentCommand command) {
		Payment payment = loadPaymentPort.loadByOrderId(command.orderId())
			.orElseThrow(() -> new CustomException(PaymentExceptionCode.NOT_FOUND));

		// 권한 검증 (도메인 서비스 활용)
		paymentValidator.validateRefundPermission(
			payment,
			command.userId(),
			command.hasMasterRole()
		);

		// 도메인 규칙 검증
		if (!payment.canRefund()) {
			throw new CustomException(PaymentExceptionCode.INVALID_STATUS);
		}

		// 외부 결제 게이트웨이 환불 처리
		try {
			paymentGatewayPort.cancel(payment.getTid(), payment.getAmount());
		} catch (KakaoPayApiException e) {
			log.error("[Payment] 카카오페이 환불 실패 - orderId: {}, 사유: {}", command.orderId(), e.getMessage());
			throw new CustomException(PaymentExceptionCode.PAYMENT_APPROVE_FAIL);
		}

		// 환불 처리
		payment.refund();
		savePaymentPort.save(payment);

		log.info("[Payment] 결제 환불 완료 - orderId: {}, amount: {}", command.orderId(), payment.getAmount());

		return PaymentRefundResponseDto.from(payment);
	}
}
