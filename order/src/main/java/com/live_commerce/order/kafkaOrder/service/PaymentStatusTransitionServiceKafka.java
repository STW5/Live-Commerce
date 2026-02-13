package com.live_commerce.order.kafkaOrder.service;


import com.live_commerce.order.application.dto.request.OrderStatusUpdateRequest;
import com.live_commerce.order.application.dto.response.OrderStatusUpdateResponse;
import com.live_commerce.order.application.exception.OrderException;
import com.live_commerce.order.application.exception.OrderExceptionCode;
import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.model.OrderStatus;
import com.live_commerce.order.domain.repository.OrderRepository;
import com.live_commerce.order.infrastructure.PaymentReadyResponseDto;
import com.live_commerce.order.infrastructure.client.feign.CouponClient;
import com.live_commerce.order.infrastructure.client.feign.PaymentClient;
import com.live_commerce.order.infrastructure.client.feign.ProductClient;
import com.live_commerce.order.infrastructure.client.request.InventoryIncreaseRequestDto;
import com.live_commerce.order.infrastructure.client.request.PaymentReadyRequestDto;
import com.live_commerce.order.infrastructure.client.response.PaymentRefundResponseDto;
import com.live_commerce.order.kafkaOrder.coupon.CouponUsedProducer;
import com.live_commerce.events.inventory.InventoryRollbackEvent;
import com.live_commerce.order.kafkaOrder.product.InventoryEventProducer;
import com.live_commerce.order.presentation.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
//결제 상태 변경
public class PaymentStatusTransitionServiceKafka {

    private final ProductClient productClient;
    private final PaymentClient paymentClient;
    private final CouponClient couponClient;
    private final OrderRepository orderRepository;
    boolean changeChangeStatus = false;

    //KAFKA
    private final InventoryEventProducer inventoryEventProducer;

    //pending -> ready -> paid

    //결제 상태 변경
    public OrderStatusUpdateResponse updateCreator(UUID orderId, OrderStatusUpdateRequest request, UUID userId, String role){
        // 주문 조회 - 해당 주문 없으면 예외처리
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderExceptionCode.NOT_FOUND));
        log.info("주문 조회 성공");

        //기존의 원래 주문 상태
        OrderStatus currentStatus = order.getStatus();

        // 새 주문 요청 상태 파싱 및 검증 - 바꾸려는 주문 상태(오타) 잘못들어오면 예외 발생
        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(request.status());  //요청받은 새로운 상태 변수에 담기
        } catch (IllegalArgumentException e) {
            throw new OrderException("잘못된 주문 상태입니다.", HttpStatus.BAD_REQUEST);
        }

        //결제 대기  : PENDING -> PAID 전 Ready(근데 요청은 PAID로 처리)
        //PaymentStatusTransitionService
        if ( (currentStatus ==OrderStatus.PENDING) &&  (newStatus == OrderStatus.PAID)){
            log.info("상태를 결제 성공으로 바꾸는 로직으로 들어옴");
            this.updateOrderStatusToPaid(order, newStatus);  //같은 서비스라서 this 붙임

            // 상태 변경 - 주문 취소는 상태 변경 불가, 같은 주문 상태 변경은 예외 발생
            order.changeStatus(OrderStatus.READY);
            changeChangeStatus = true;
            log.info("상태 변경->  대기 상태로 변경 성공");
            log.info("결제승인을 하려면 로그에 출력된 url 결제 후,");
            log.info("tid값과 orderId, page의 Token값을 넣어서 http://localhost:19091/api/v1/payments/approve api 실행후");
            log.info("notifyPaymentSuccess api 요청을 보내세요");
            //service 바로 종료
            return OrderStatusUpdateResponse.fromOrder(order);
        }

        //결제 취소 : PAID -> REFUNDED
        //RefundStatusTransitionService
        //PAID상태인 경우에만, 상품 상태 변경이 일어나 취소된다면 -> 재고 복구, 결제 취소 처리
        if ( (currentStatus==OrderStatus.PAID) &&  (newStatus == OrderStatus.REFUNDED)) {
            log.info("결제 취소 로직에 들어옴");

            // 1. [결제 취소 처리] 필요 시 결제 서비스 호출(주문 번호와 쿠폰 적용 후 최종 결제 금액을 payment로 보내준다.)
            ApiResponse<PaymentRefundResponseDto> responseRefund= paymentClient.refundPayment(order.getId());
            log.info("결제 취소 요청 들어감");

            if(responseRefund == null){
                throw new OrderException("결제 취소 요청 실패. 다시 시도하기", HttpStatus.BAD_REQUEST);
            }

            //TODO KAFKA
            // 2. [재고 복구] 상품 서비스 호출 - 주문의 상품 id와 주문 상품 개수를 보내준다. 재고 복구는 Product에서 로직 처리
            inventoryEventProducer.sendInventoryRollbackEvent(
                    InventoryRollbackEvent.of(order.getId(), order.getProductId(), order.getProductQuantity(), "사용자 결제 취소"));
            log.info("결제 취소 후 재고 복구완료");
            log.info("결제 취소 완료");

            // 상태 변경 - 주문 취소는 상태 변경 불가, 같은 주문 상태 변경은 예외 발생
            order.changeStatus(newStatus);
            changeChangeStatus = true;
            log.info("상태 변경 성공");
        }

        //주문 접수 -> 주문 취소 : PENDING -> CANCELLED
        //그냥 통과.
        if ( (currentStatus==OrderStatus.PENDING) && (newStatus == OrderStatus.CANCELLED)){
            order.changeStatus(newStatus);
            changeChangeStatus = true;
            log.info("상태 변경 성공");
        }

        if(!changeChangeStatus){
            throw new OrderException("주문 상태 요청이 잘못되었습니다. 다시 수정해서 시도해주세요", HttpStatus.BAD_REQUEST);
        }
        return OrderStatusUpdateResponse.fromOrder(order);
    }

    //결제 대기 : PENDING -> PAID
    @Transactional
    public void updateOrderStatusToPaid(Order order, OrderStatus newStatus) {

        // 결제 대기 -> 승인 -> 결제 완료 요청
        // 주문 아이디와 최종 결제 금액을 payment로 보내줌 (order -> Panyemt)
        BigDecimal amount = BigDecimal.valueOf(order.getFinalPaidPrice());
        log.info("결제 예상금액 : " + amount);
        log.info("주문 id" + order.getId());
        log.info("결제 상품 id" + order.getId());

        /////// 결제 대기 요청
        //결제 대기 feign요청 -> 결제 대기 결과값 응답으로 받기
        ApiResponse<PaymentReadyResponseDto> responseReady= paymentClient.readyPayment(
                new PaymentReadyRequestDto(order.getId(), amount, order.getProductId().toString()));
        log.info("결제 대기 응답 받아오기");
        PaymentReadyResponseDto res = responseReady.getData();
        String tid = res.tid();
        String url = res.nextRedirectUrl();
        log.info(tid);
        log.info(url);
        if(responseReady == null){
            throw new OrderException("결제 대기 요청 실패. 다시 시도하기", HttpStatus.BAD_REQUEST);
        }
        log.info("결제 대기 로직 수행 완료");
    }
}