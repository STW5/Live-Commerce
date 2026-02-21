package com.live_commerce.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EnableFeignClients(basePackages = "com.live_commerce.order.infrastructure.client")
@SpringBootApplication
@EntityScan(basePackages = {"com.live_commerce.order", "com.live_commerce.common"})
@EnableJpaRepositories(basePackages = {"com.live_commerce.order", "com.live_commerce.common"})
public class OrderApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderApplication.class, args);
	}

}
