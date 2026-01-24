package com.live_commerce.payment.application.port.out;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 결제 만료 관리 포트 (Outbound)
 * - Redis TTL 기반 결제 타임아웃 관리
 */
public interface ManagePaymentExpirationPort {
	void setExpiration(UUID orderId, UUID paymentId, long timeout, TimeUnit unit);

	void removeExpiration(UUID orderId);
}
