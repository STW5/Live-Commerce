package com.live_commerce.payment.application.service;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.payment.application.dto.response.PaymentReadyResponseDto;
import com.live_commerce.payment.application.exception.CustomException;
import com.live_commerce.payment.application.exception.PaymentExceptionCode;
import com.live_commerce.payment.application.port.in.ReadyPaymentUseCase;
import com.live_commerce.payment.application.port.out.LoadPaymentPort;
import com.live_commerce.payment.application.port.out.ManagePaymentExpirationPort;
import com.live_commerce.payment.application.port.out.PaymentGatewayPort;
import com.live_commerce.payment.application.port.out.SavePaymentPort;
import com.live_commerce.payment.domain.model.Payment;
import com.live_commerce.payment.domain.model.PaymentStatus;
import com.live_commerce.payment.infrastructure.lock.DistributedLock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 준비 서비스
 * - 단일 책임: 결제 준비 유스케이스만 처리
 * - 분산 락을 통한 중복 결제 방지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReadyPaymentService implements ReadyPaymentUseCase {

	private static final long PAYMENT_TIMEOUT_MINUTES = 10L;

	private final LoadPaymentPort loadPaymentPort;
	private final SavePaymentPort savePaymentPort;
	private final PaymentGatewayPort paymentGatewayPort;
	private final ManagePaymentExpirationPort managePaymentExpirationPort;

	@Override
	@DistributedLock(key = "#command.orderId()")
	@Transactional
	public PaymentReadyResponseDto ready(ReadyPaymentCommand command) {
		// 중복 결제 검증
		loadPaymentPort.loadByOrderId(command.orderId()).ifPresent(existing -> {
			if (existing.getStatus() != PaymentStatus.FAILED) {
				throw new CustomException(PaymentExceptionCode.DUPLICATE_PAYMENT);
			}
		});

		// 외부 결제 게이트웨이 호출
		PaymentGatewayPort.PaymentReadyResult readyResult = paymentGatewayPort.ready(
			command.userId(),
			command.orderId(),
			command.amount(),
			command.itemName()
		);

		// 결제 엔티티 생성
		Payment payment = Payment.of(command.userId(), command.orderId(), command.amount());
		payment.assignTid(readyResult.tid());
		Payment savedPayment = savePaymentPort.save(payment);

		// 결제 만료 타임아웃 설정 (10분)
		managePaymentExpirationPort.setExpiration(
			command.orderId(),
			savedPayment.getId(),
			PAYMENT_TIMEOUT_MINUTES,
			TimeUnit.MINUTES
		);

		log.info("[Payment] 결제 준비 완료 - orderId: {}, tid: {}", command.orderId(), readyResult.tid());

		return PaymentReadyResponseDto.from(readyResult);
	}
}
