package com.live_commerce.payment.application.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.live_commerce.payment.application.port.out.PaymentGatewayPort;
import com.live_commerce.payment.domain.model.Payment;
import com.live_commerce.payment.infrastructure.adapter.persistence.PaymentJpaEntity;
import com.live_commerce.payment.infrastructure.adapter.persistence.PaymentJpaRepository;

import lombok.extern.slf4j.Slf4j;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:distlocktestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@Slf4j
public class PaymentDistributedLockTest {

	@Autowired
	private RedissonClient redissonClient;

	@Autowired
	private PaymentJpaRepository paymentJpaRepository;

	@MockitoBean
	private PaymentGatewayPort paymentGatewayPort;

	@DisplayName("leaseTime 이후 다른 스레드가 락 재획득 - DB 저장 확인")
	@Test
	void testRedissonLockBreakByLeaseTime_withDB() throws InterruptedException {
		String lockKey = "lock:test:" + UUID.randomUUID();
		RLock lock = redissonClient.getLock(lockKey);
		UUID orderId1 = UUID.randomUUID();
		UUID orderId2 = UUID.randomUUID();

		Runnable task1 = () -> {
			try {
				if (lock.tryLock(100, 1000, TimeUnit.MILLISECONDS)) {
					Payment p1 = Payment.of(UUID.randomUUID(), orderId1, BigDecimal.valueOf(1000));
					paymentJpaRepository.save(PaymentJpaEntity.from(p1));
					Thread.sleep(2000);
				}
			} catch (Exception e) {
				log.error("task1 예외", e);
			} finally {
				try {
					lock.unlock();
				} catch (Exception e) {
					log.warn("unlock 실패: {}", e.getMessage());
				}
			}
		};

		Runnable task2 = () -> {
			try {
				Thread.sleep(1500);
				if (lock.tryLock(100, 1000, TimeUnit.MILLISECONDS)) {
					Payment p2 = Payment.of(UUID.randomUUID(), orderId2, BigDecimal.valueOf(2000));
					paymentJpaRepository.save(PaymentJpaEntity.from(p2));
					lock.unlock();
				}
			} catch (Exception e) {
				log.error("task2 예외", e);
			}
		};

		ExecutorService executor = Executors.newFixedThreadPool(2);
		executor.submit(task1);
		executor.submit(task2);
		executor.shutdown();
		executor.awaitTermination(5, TimeUnit.SECONDS);

		assertTrue(paymentJpaRepository.findByOrderId(orderId1).isPresent());
		assertTrue(paymentJpaRepository.findByOrderId(orderId2).isPresent());
	}

	@DisplayName("leaseTime 초과 후 unlock 예외 확인")
	@Test
	void testRedissonLockLeak() throws InterruptedException {
		String lockKey = "lock:test:" + UUID.randomUUID();
		RLock lock = redissonClient.getLock(lockKey);

		Runnable thread1 = () -> {
			try {
				if (lock.tryLock(100, 1000, TimeUnit.MILLISECONDS)) {
					log.info("[T1] 락 획득");
					Thread.sleep(2000);
					log.info("[T1] 작업 끝");
				}
			} catch (Exception e) {
				log.error("thread1 예외", e);
			} finally {
				try {
					lock.unlock();
					log.info("[T1] 락 해제");
				} catch (Exception e) {
					log.warn("[T1] 락 해제 실패: {}", e.getMessage());
				}
			}
		};

		Runnable thread2 = () -> {
			try {
				Thread.sleep(1500);
				if (lock.tryLock(100, 1000, TimeUnit.MILLISECONDS)) {
					log.info("[T2] 락 획득");
					Thread.sleep(500);
					log.info("[T2] 작업 끝");
					lock.unlock();
				}
			} catch (Exception e) {
				log.error("thread2 예외", e);
			}
		};

		ExecutorService executor = Executors.newFixedThreadPool(2);
		executor.submit(thread1);
		executor.submit(thread2);
		executor.shutdown();
		executor.awaitTermination(5, TimeUnit.SECONDS);
	}

	@DisplayName("watchdog이 leaseTime 연장 - 장기 작업 유지")
	@Test
	void testRedissonLockWithWatchdog() throws InterruptedException {
		String lockKey = "lock:test:" + UUID.randomUUID();
		RLock lock = redissonClient.getLock(lockKey);

		Runnable longTask = () -> {
			try {
				lock.lock();
				log.info("[T1] 락 획득 - watchdog");
				Thread.sleep(15000);
				log.info("[T1] 작업 완료");
			} catch (Exception e) {
				log.error("watchdog 예외", e);
			} finally {
				lock.unlock();
				log.info("[T1] 락 해제");
			}
		};

		ExecutorService executor = Executors.newFixedThreadPool(1);
		executor.submit(longTask);
		executor.shutdown();
		executor.awaitTermination(20, TimeUnit.SECONDS);
	}

	@DisplayName("락 획득 실패 시 재시도 - eventually 성공")
	@Test
	void testTryLockWithRetry() throws InterruptedException {
		String lockKey = "lock:test:" + UUID.randomUUID();
		RLock lock = redissonClient.getLock(lockKey);

		UUID retryOrderId = UUID.randomUUID();
		AtomicInteger attemptCount = new AtomicInteger(0);

		Runnable longTask = () -> {
			try {
				lock.lock();
				log.info("[LONG] 락 보유 중...");
				Thread.sleep(2000);
			} catch (Exception e) {
				log.error("longTask 예외", e);
			} finally {
				lock.unlock();
				log.info("[LONG] 락 해제");
			}
		};

		Runnable retryingTask = () -> {
			int retry = 10;
			while (retry-- > 0) {
				attemptCount.incrementAndGet();
				try {
					if (lock.tryLock(100, 1000, TimeUnit.MILLISECONDS)) {
						log.info("[RETRY] 락 획득 성공");
						Payment p = Payment.of(UUID.randomUUID(), retryOrderId, BigDecimal.valueOf(7777));
						paymentJpaRepository.save(PaymentJpaEntity.from(p));
						lock.unlock();
						break;
					} else {
						log.info("[RETRY] 락 획득 실패, 재시도 남음: {}", retry);
						Thread.sleep(400);
					}
				} catch (Exception e) {
					log.error("retryingTask 예외", e);
				}
			}
		};

		ExecutorService executor = Executors.newFixedThreadPool(2);
		executor.submit(longTask);
		Thread.sleep(100);
		executor.submit(retryingTask);
		executor.shutdown();
		executor.awaitTermination(5, TimeUnit.SECONDS);

		Optional<PaymentJpaEntity> result = paymentJpaRepository.findByOrderId(retryOrderId);
		log.info("[TEST] 시도 횟수: {}", attemptCount.get());
		log.info("[TEST] 저장 여부: {}", result.isPresent());

		assertTrue(result.isPresent(), "락 획득 실패로 저장되지 않았습니다");
	}

	@DisplayName("Redis 키 강제 삭제 후 unlock 예외 확인")
	@Test
	void testUnlockFailurePreventsReentry() throws InterruptedException {
		String lockKey = "lock:test:" + UUID.randomUUID();
		RLock lock = redissonClient.getLock(lockKey);

		lock.lock();
		log.info("[TEST] 락 획득");

		redissonClient.getBucket(lockKey).delete();
		log.info("[TEST] Redis key 강제 삭제");

		try {
			lock.unlock();
			fail("예외가 발생해야 합니다");
		} catch (Exception e) {
			log.warn("[TEST] 예상된 락 해제 실패 발생: {}", e.getMessage());
		}
	}
}
