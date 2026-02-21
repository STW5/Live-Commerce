package com.live_commerce.coupon;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 통합 컨텍스트 로드 테스트 - 실행 환경(DB, Redis, Kafka, Config Server) 필요.
 * 로컬/CI 환경에서는 인프라 없이 실행 불가.
 */
@Disabled("통합 환경(Config Server, DB, Redis, Kafka)이 필요한 테스트 - 인프라 구동 후 실행")
@SpringBootTest
class CouponApplicationTests {

	@Test
	void contextLoads() {
	}

}
