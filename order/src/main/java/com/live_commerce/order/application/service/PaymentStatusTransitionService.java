package com.live_commerce.order.application.service;

import com.live_commerce.order.application.dto.request.OrderStatusUpdateRequest;
import com.live_commerce.order.application.dto.response.OrderStatusUpdateResponse;
import com.live_commerce.order.application.exception.OrderException;
import com.live_commerce.order.application.exception.OrderExceptionCode;
import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.model.OrderStatus;
import com.live_commerce.order.domain.port.out.OrderRepositoryPort;
import com.live_commerce.order.infrastructure.PaymentReadyResponseDto;
import com.live_commerce.order.infrastructure.client.feign.CouponClient;
import com.live_commerce.order.infrastructure.client.feign.PaymentClient;
import com.live_commerce.order.infrastructure.client.feign.ProductClient;
import com.live_commerce.order.infrastructure.client.request.InventoryIncreaseRequestDto;
import com.live_commerce.order.infrastructure.client.request.PaymentReadyRequestDto;
import com.live_commerce.order.infrastructure.client.response.PaymentRefundResponseDto;
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
@Deprecated(since = "hexagonal-ddd-order", forRemoval = true)
public class PaymentStatusTransitionService {
    private final ProductClient productClient;
    private final PaymentClient paymentClient;
    private final CouponClient couponClient;
    private final OrderRepositoryPort orderRepositoryPort;
    boolean changeChangeStatus = false;

    //결제 상태 변경
    public OrderStatusUpdateResponse updateCreator(UUID orderId, OrderStatusUpdateRequest request, UUID userId, String role) {
        // 주문 조회
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderExceptionCode.NOT_FOUND));
        log.info("주문 조회 성공");

        OrderStatus currentStatus = order.getStatus();

        // 새 주문 요청 상태 파싱 및 검증
        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(request.status());
        } catch (IllegalArgumentException e) {
            throw new OrderException("잘못된 주문 상태입니다.", HttpStatus.BAD_REQUEST);
        }

        // 결제 대기: PENDING -> PAID
        if ((currentStatus == OrderStatus.PENDING) && (newStatus == OrderStatus.PAID)) {
            log.info("상태를 결제 성공으로 바꾸는 로직으로 들어옴");
            this.updateOrderStatusToPaid(order, newStatus);

            order.changeStatus(OrderStatus.READY);
            orderRepositoryPort.save(order);
            changeChangeStatus = true;
            log.info("상태 변경-> 대기 상태로 변경 성공");
            return OrderStatusUpdateResponse.fromOrder(order);
        }

        // 결제 취소: PAID -> REFUNDED
        if ((currentStatus == OrderStatus.PAID) && (newStatus == OrderStatus.REFUNDED)) {
            log.info("결제 취소 로직에 들어옴");

            ApiResponse<PaymentRefundResponseDto> responseRefund = paymentClient.refundPayment(order.getId());
            log.info("결제 취소 요청 들어감");

            if (responseRefund == null) {
                throw new OrderException("결제 취소 요청 실패. 다시 시도하기", HttpStatus.BAD_REQUEST);
            }

            productClient.increaseInventory(new InventoryIncreaseRequestDto(
                    order.getProductId(),
                    order.getProductQuantity()));
            log.info("결제 취소 후 재고 복구완료");

            order.changeStatus(newStatus);
            orderRepositoryPort.save(order);
            changeChangeStatus = true;
            log.info("상태 변경 성공");
        }

        // 주문 접수 -> 주문 취소: PENDING -> CANCELLED
        if ((currentStatus == OrderStatus.PENDING) && (newStatus == OrderStatus.CANCELLED)) {
            order.changeStatus(newStatus);
            orderRepositoryPort.save(order);
            changeChangeStatus = true;
            log.info("상태 변경 성공");
        }

        if (!changeChangeStatus) {
            throw new OrderException("주문 상태 요청이 잘못되었습니다. 다시 수정해서 시도해주세요", HttpStatus.BAD_REQUEST);
        }
        return OrderStatusUpdateResponse.fromOrder(order);
    }

    // 결제 대기: PENDING -> PAID
    @Transactional
    public void updateOrderStatusToPaid(Order order, OrderStatus newStatus) {
        BigDecimal amount = BigDecimal.valueOf(order.getFinalPaidPrice());
        log.info("결제 예상금액 : " + amount);
        log.info("주문 id" + order.getId());

        ApiResponse<PaymentReadyResponseDto> responseReady = paymentClient.readyPayment(
                new PaymentReadyRequestDto(order.getId(), amount, order.getProductId().toString()));
        log.info("결제 대기 응답 받아오기");
        PaymentReadyResponseDto res = responseReady.getData();
        String tid = res.tid();
        String url = res.nextRedirectUrl();
        log.info(tid);
        log.info(url);
        if (responseReady == null) {
            throw new OrderException("결제 대기 요청 실패. 다시 시도하기", HttpStatus.BAD_REQUEST);
        }
        log.info("결제 대기 로직 수행 완료");
    }
}
