package com.live_commerce.user;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.config.import=optional:configserver:")
@ActiveProfiles("test")
class UserApplicationTests {

	@Test
	void contextLoads() {
	}

}
