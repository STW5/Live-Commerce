package com.live_commerce.order.kafkaOrder.service;

import com.live_commerce.order.application.exception.OrderException;
import com.live_commerce.order.application.exception.OrderExceptionCode;
import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.repository.OrderRepository;
import com.live_commerce.common.saga.SagaStateRepository;
import com.live_commerce.events.inventory.InventoryDecreaseRequestEvent;
import com.live_commerce.events.payment.PaymentCompletedEvent;
import com.live_commerce.order.kafkaOrder.coupon.CouponUsedEvent;
import com.live_commerce.order.kafkaOrder.coupon.CouponUsedProducer;
import com.live_commerce.order.kafkaOrder.product.InventoryEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.live_commerce.order.domain.model.OrderStatus.PAID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSuccessServiceKafka {

    private final OrderRepository orderRepository;
    private final CouponUsedProducer couponUsedProducer;
    private final InventoryEventProducer inventoryEventProducer;
    private final SagaStateRepository sagaStateRepository;

    //TODO KAFKA
    //결제 처리 응답값 가져오기
    @Transactional
    public String updatePaymentSuccessKafka(PaymentCompletedEvent event){
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new OrderException(OrderExceptionCode.NOT_FOUND));
        log.info("주문 들고오기 성공");
        if(event.message() == null){
            throw new OrderException("결제 상태가 COMPLETED가 아닌 상태입니다. 다시 결제해주세요 ", HttpStatus.FORBIDDEN);
        }

        UUID userId = order.getUserId();
        log.info("유저아이디 들고오기 성공" + userId);

        //상태 변경 PAID로 변경
        order.changeStatus(PAID);
        log.info("READY 에서 PAID로 변경 성공!");

        //TODO KAFKA 처리
        // 6. 재고 감소 진행
        InventoryDecreaseRequestEvent eventProductDecrease = InventoryDecreaseRequestEvent.of(order.getId(), order.getProductId(), order.getProductQuantity());
        inventoryEventProducer.sendOrderRequestedInventoryEvent(eventProductDecrease);
        log.info("재고 감소 성공!!");

        //TODO KAFKA
        // 7. 쿠폰 사용 처리
        if(order.getCouponId() != null){
            CouponUsedEvent eventCoupon = new CouponUsedEvent(order.getCouponId(), userId);
            couponUsedProducer.sendCouponUsedEvent(eventCoupon);
        }

        // 8. Saga 상태 완료 처리
        updateSagaToCompleted(order.getId());

        return "결제 성공 처리 완료";
    }

    /**
     * Saga 상태를 COMPLETED로 전환
     */
    private void updateSagaToCompleted(UUID orderId) {
        try {
            sagaStateRepository.findByAggregateId(orderId).ifPresent(saga -> {
                saga.complete();
                sagaStateRepository.save(saga);
                log.info("[Saga] 상태 완료 처리 - orderId: {}", orderId);
            });
        } catch (Exception e) {
            log.warn("[Saga] 완료 상태 업데이트 실패 (무시) - orderId: {}, error: {}", orderId, e.getMessage());
        }
    }
}
