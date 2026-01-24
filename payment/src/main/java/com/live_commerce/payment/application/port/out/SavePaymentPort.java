package com.live_commerce.payment.application.port.out;

import com.live_commerce.payment.domain.model.Payment;

/**
 * 결제 저장 포트 (Outbound)
 */
public interface SavePaymentPort {
	Payment save(Payment payment);
}
