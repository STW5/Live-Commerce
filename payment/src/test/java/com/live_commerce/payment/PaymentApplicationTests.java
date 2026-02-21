package com.live_commerce.payment;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.live_commerce.payment.application.port.out.ManagePaymentExpirationPort;
import com.live_commerce.payment.application.port.out.PaymentGatewayPort;

@SpringBootTest
@ActiveProfiles("test")
class PaymentApplicationTests {

	@MockitoBean
	private PaymentGatewayPort paymentGatewayPort;
	@MockitoBean
	private ManagePaymentExpirationPort managePaymentExpirationPort;
	@MockitoBean
	private RedissonClient redissonClient;
	@MockitoBean
	private RedisMessageListenerContainer redisContainer;

	@Test
	void contextLoads() {
	}

}
