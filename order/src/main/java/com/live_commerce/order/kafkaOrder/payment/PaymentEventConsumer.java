package com.live_commerce.order.kafkaOrder.payment;

import com.live_commerce.events.payment.PaymentCompletedEvent;
import com.live_commerce.events.payment.PaymentFailedEvent;
import com.live_commerce.order.kafkaOrder.service.PaymentFailureServiceKafka;
import com.live_commerce.order.kafkaOrder.service.PaymentSuccessServiceKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final PaymentSuccessServiceKafka paymentSuccessServiceKafka;
    private final PaymentFailureServiceKafka paymentFailureServiceKafka;

    //payment -> order
    private static final String COMPLETED_TOPIC = "payment-completed";
    private static final String FAILED_TOPIC = "payment-failed";

    @KafkaListener(
            topics = COMPLETED_TOPIC,
            groupId = "order"
    )
    public void listenPaymentCompleted(PaymentCompletedEvent msg) {
        log.info("✅ 결제 성공 이벤트 수신(kafka): orderId={}, message={}", msg.orderId(), msg.message());
        paymentSuccessServiceKafka.updatePaymentSuccessKafka(msg);
    }

    @KafkaListener(
            topics = FAILED_TOPIC,
            groupId = "order"
    )
    public void listenPaymentFailed(PaymentFailedEvent event) {
        log.info("❌ 결제 실패 이벤트 수신(kafka): orderId={}, message={}", event.orderId(), event.message());
        paymentFailureServiceKafka.handlePaymentFailure(event);
    }
}
