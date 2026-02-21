package com.live_commerce.order.application.service;

import com.live_commerce.order.application.dto.request.OrderCreateRequest;
import com.live_commerce.order.application.dto.request.OrderStatusUpdateRequest;
import com.live_commerce.order.application.dto.request.OrderUpdateRequest;
import com.live_commerce.order.application.dto.response.*;
import com.live_commerce.order.application.exception.OrderException;
import com.live_commerce.order.application.exception.OrderExceptionCode;
import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.port.out.OrderRepositoryPort;
import com.live_commerce.order.infrastructure.client.feign.CouponClient;
import com.live_commerce.order.infrastructure.client.feign.PaymentClient;
import com.live_commerce.order.infrastructure.client.feign.ProductClient;
import com.live_commerce.order.infrastructure.client.request.InventoryDecreaseRequestDto;
import com.live_commerce.order.infrastructure.client.request.PaymentSuccessRequest;
import com.live_commerce.order.infrastructure.client.response.PaymentSuccessResponseOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.live_commerce.order.domain.model.OrderStatus.PAID;

/**
 * @deprecated 레거시 서비스. 신규 코드는 UseCase 인터페이스 사용 권장.
 * createOrder → CreateOrderUseCase, updateOrderStatus → PaymentStatusTransitionService
 */
@Deprecated(since = "hexagonal-ddd-order", forRemoval = true)
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    // Port 기반 Repository (hexagonal)
    private final OrderRepositoryPort orderRepositoryPort;

    //service 호출
    @Lazy
    private final PaymentStatusTransitionService paymentStatusTransitionService;
    private final OrderCreateService orderCreateService;
    private final OrderModificationService orderModificationService;

    //feign 요청
    private final ProductClient productClient;
    private final PaymentClient paymentClient;
    private final CouponClient couponClient;

    //주문 생성 service
    @Transactional
    public OrderCreateResponse createOrder(OrderCreateRequest request, UUID userId) {
        return orderCreateService.orderCreator(request, userId);
    }

    //주문 전체 조회 service
    @Transactional(readOnly = true)
    public OrderGetResponse getOrders(final int page, final int size, final String sort, UUID userId, String role) {
        Pageable pageable = getPageable(page, size, sort);

        //권한 검증 - CUSTOMER 본인 주문만 조회 가능, 나머지 권한 다 조회 가능
        if ("ROLE_CUSTOMER".equals(role)) {
            return OrderGetResponse.of(orderRepositoryPort.findAllByUserId(userId, pageable));
        } else {
            return OrderGetResponse.of(orderRepositoryPort.findAll(pageable));
        }
    }

    //주문 단건 조회 service
    @Transactional(readOnly = true)
    public OrderGetOneResponse getOrder(final UUID id, UUID userId, String role) {
        Order order = orderRepositoryPort.findById(id)
                .orElseThrow(() -> new OrderException(OrderExceptionCode.NOT_FOUND));

        // 권한 검증 - CUSTOMER는 본인 주문만 조회 가능
        validateCustomerOrderAccess(role, order.getUserId(), userId);

        return OrderGetOneResponse.of(order);
    }

    //주문 수정 service - 주문 상태 변경을 일어나지 않음.
    @Transactional
    public OrderUpdateResponse updateOrder(UUID orderId, OrderUpdateRequest request, UUID userId, String role) {
        return orderModificationService.updateCreator(orderId, request, userId, role);
    }

    //주문 상태 변경 SERVICE
    @Transactional
    public OrderStatusUpdateResponse updateOrderStatus(UUID orderId, OrderStatusUpdateRequest request, UUID userId, String role) {
        return paymentStatusTransitionService.updateCreator(orderId, request, userId, role);
    }

    //주문 삭제 SERVICE
    @Transactional
    public OrderDeleteResponse deleteOrder(UUID orderId, UUID userId, String role) {
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderExceptionCode.NOT_FOUND));

        // 권한 검증
        validateCustomerOrderAccess(role, order.getUserId(), userId);

        // 소프트 삭제: deletedAt 설정은 OrderJpaEntity(BaseJpaEntity)가 담당하므로
        // 여기서는 영속화 어댑터를 통해 삭제 플래그를 처리
        orderRepositoryPort.softDelete(orderId, userId.toString());
        return OrderDeleteResponse.of(orderId);
    }

    //페이징 함수
    private Pageable getPageable(final int page, final int size, final String sort) {
        if (sort == null || sort.isBlank()) {
            return PageRequest.of(page, size);
        }
        String[] sortParams = sort.split(",");
        List<Sort.Order> orders = new ArrayList<>();
        for (String param : sortParams) {
            String[] fieldAndDirection = param.trim().split("[- ]");
            if (fieldAndDirection.length != 2) {
                throw new IllegalArgumentException(
                        "Invalid sort parameter format. Expected 'field direction' (e.g., 'name asc').");
            }
            String field = fieldAndDirection[0].trim();
            String direction = fieldAndDirection[1].trim().toUpperCase();
            if (!direction.equals("ASC") && !direction.equals("DESC")) {
                throw new IllegalArgumentException("Invalid sort direction. Use 'asc' or 'desc'.");
            }
            Sort.Direction dir = Sort.Direction.fromString(direction);
            orders.add(new Sort.Order(dir, field));
        }
        Sort sortObj = Sort.by(orders);
        return PageRequest.of(page, size, sortObj);
    }

    //고객의 경우 본인의 주문만 수정가능
    public void validateCustomerOrderAccess(String role, UUID orderUserId, UUID currentUserId) {
        if ("ROLE_CUSTOMER".equals(role) && !orderUserId.equals(currentUserId)) {
            throw new OrderException("고객은 자신의 주문만 조회, 수정할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
    }

    //결제 처리 응답값 가져오기
    @Transactional
    public PaymentSuccessResponseOrder updatePaymentSuccess(UUID orderId, PaymentSuccessRequest request) {
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderExceptionCode.NOT_FOUND));
        log.info("주문 들고오기 성공");
        if (!(request.success())) {
            throw new OrderException("결제 상태가 COMPLETED가 아닌 상태입니다. 다시 결제해주세요 ", HttpStatus.FORBIDDEN);
        }

        order.changeStatus(PAID);
        orderRepositoryPort.save(order);
        log.info("READY 에서 PAID로 변경 성공!");

        productClient.decreaseInventory(new InventoryDecreaseRequestDto(order.getProductId(), order.getProductQuantity()));

        if (order.getCouponId() != null) {
            couponClient.useCoupon(order.getCouponId());
        }
        return new PaymentSuccessResponseOrder(order.getId(), request.success());
    }
}
