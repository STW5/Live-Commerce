package com.live_commerce.user.application.port.out;

import java.util.UUID;

public interface PublishUserEventPort {
	void publishFirstJoinCouponEvent(UUID userId);
}
