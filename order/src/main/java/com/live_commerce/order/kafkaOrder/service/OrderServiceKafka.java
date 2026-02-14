package com.live_commerce.order.kafkaOrder.service;


import com.live_commerce.order.application.dto.request.OrderCreateRequest;
import com.live_commerce.order.application.dto.request.OrderStatusUpdateRequest;
import com.live_commerce.order.application.dto.request.OrderUpdateRequest;
import com.live_commerce.order.application.dto.response.*;
import com.live_commerce.order.application.exception.OrderException;
import com.live_commerce.order.application.exception.OrderExceptionCode;
import com.live_commerce.order.application.service.OrderModificationService;
import com.live_commerce.order.domain.model.Order;
import com.live_commerce.order.domain.port.out.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceKafka {
    private final OrderRepositoryPort orderRepositoryPort;

    private final OrderCreateServiceKafka orderCreateServiceKafka;
    @Lazy
    private final PaymentStatusTransitionServiceKafka paymentStatusTransitionServiceKafka;
    private final OrderModificationService orderModificationService;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public OrderCreateResponse createOrder(OrderCreateRequest request, UUID userId) {
        return orderCreateServiceKafka.orderCreator(request, userId);
    }

    @Transactional(readOnly = true)
    public OrderGetResponse getOrders(final int page, final int size, final String sort, UUID userId, String role) {
        Pageable pageable = getPageable(page, size, sort);

        if ("ROLE_CUSTOMER".equals(role)) {
            return OrderGetResponse.of(orderRepositoryPort.findAllByUserId(userId, pageable));
        } else {
            return OrderGetResponse.of(orderRepositoryPort.findAll(pageable));
        }
    }

    @Transactional(readOnly = true)
    public OrderGetOneResponse getOrder(final UUID id, UUID userId, String role) {
        Order order = orderRepositoryPort.findById(id)
                .orElseThrow(() -> new OrderException(OrderExceptionCode.NOT_FOUND));

        validateCustomerOrderAccess(role, order.getUserId(), userId);

        return OrderGetOneResponse.of(order);
    }

    @Transactional
    public OrderUpdateResponse updateOrder(UUID orderId, OrderUpdateRequest request, UUID userId, String role) {
        return orderModificationService.updateCreator(orderId, request, userId, role);
    }

    @Transactional
    public OrderStatusUpdateResponse updateOrderStatus(UUID orderId, OrderStatusUpdateRequest request, UUID userId, String role) {
        return paymentStatusTransitionServiceKafka.updateCreator(orderId, request, userId, role);
    }

    @Transactional
    public OrderDeleteResponse deleteOrder(UUID orderId, UUID userId, String role) {
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderExceptionCode.NOT_FOUND));

        validateCustomerOrderAccess(role, order.getUserId(), userId);

        orderRepositoryPort.softDelete(orderId, userId.toString());
        return OrderDeleteResponse.of(orderId);
    }

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

    public void validateCustomerOrderAccess(String role, UUID orderUserId, UUID currentUserId) {
        if ("ROLE_CUSTOMER".equals(role) && !orderUserId.equals(currentUserId)) {
            throw new OrderException("고객은 자신의 주문만 조회, 수정할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
    }

    public void sendMessage(String topic, String key, String message) {
        for (int i = 0; i < 10; i++) {
            kafkaTemplate.send(topic, key, message + " " + i);
        }
    }
}
