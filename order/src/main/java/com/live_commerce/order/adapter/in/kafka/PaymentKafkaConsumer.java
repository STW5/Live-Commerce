package com.live_commerce.order.adapter.in.kafka;

import com.live_commerce.events.payment.PaymentCompletedEvent;
import com.live_commerce.events.payment.PaymentFailedEvent;
import com.live_commerce.order.domain.port.in.HandlePaymentFailureUseCase;
import com.live_commerce.order.domain.port.in.HandlePaymentSuccessUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 결제 이벤트 Kafka Consumer (Inbound Adapter)
 *
 * [핵사고날 아키텍처 적용]
 * - UseCase 인터페이스(Inbound Port)만 주입 - 구현체 직접 의존 없음
 * - Kafka 관련 코드(인프라)와 비즈니스 로직 완전 분리
 *
 * [기존 kafkaOrder/payment/PaymentEventConsumer.java 대체]
 * 기존 파일은 하위 호환성을 위해 유지, 향후 삭제 예정
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentKafkaConsumer {

    // ✅ UseCase 인터페이스 주입 (구현체 직접 주입 없음)
    private final HandlePaymentSuccessUseCase handlePaymentSuccessUseCase;
    private final HandlePaymentFailureUseCase handlePaymentFailureUseCase;

    private static final String COMPLETED_TOPIC = "payment-completed";
    private static final String FAILED_TOPIC = "payment-failed";
    private static final String GROUP_ID = "order-hexagonal";

    @KafkaListener(topics = COMPLETED_TOPIC, groupId = GROUP_ID)
    public void listenPaymentCompleted(PaymentCompletedEvent msg) {
        log.info("[PaymentKafkaConsumer] 결제 성공 이벤트 수신 - orderId={}", msg.orderId());
        handlePaymentSuccessUseCase.handle(msg.orderId(), msg.message());
    }

    @KafkaListener(topics = FAILED_TOPIC, groupId = GROUP_ID)
    public void listenPaymentFailed(PaymentFailedEvent event) {
        log.info("[PaymentKafkaConsumer] 결제 실패 이벤트 수신 - orderId={}", event.orderId());
        handlePaymentFailureUseCase.handle(event.orderId(), event.message());
    }
}
