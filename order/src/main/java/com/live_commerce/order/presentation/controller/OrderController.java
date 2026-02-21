package com.live_commerce.order.presentation.controller;

import com.live_commerce.order.application.dto.command.CreateOrderCommand;
import com.live_commerce.order.application.dto.command.UpdateOrderCommand;
import com.live_commerce.order.application.dto.request.OrderCreateRequest;
import com.live_commerce.order.application.dto.request.OrderStatusUpdateRequest;
import com.live_commerce.order.application.dto.response.OrderStatusUpdateResponse;
import com.live_commerce.order.application.dto.result.CreateOrderResult;
import com.live_commerce.order.application.dto.result.OrderDeleteResult;
import com.live_commerce.order.application.dto.result.OrderGetResult;
import com.live_commerce.order.application.dto.result.OrderListResult;
import com.live_commerce.order.application.dto.result.OrderUpdateResult;
import com.live_commerce.order.application.service.OrderService;
import com.live_commerce.order.domain.port.in.CreateOrderUseCase;
import com.live_commerce.order.domain.port.in.DeleteOrderUseCase;
import com.live_commerce.order.domain.port.in.GetOrderListUseCase;
import com.live_commerce.order.domain.port.in.GetOrderUseCase;
import com.live_commerce.order.domain.port.in.UpdateOrderUseCase;
import com.live_commerce.order.infrastructure.client.request.PaymentSuccessRequest;
import com.live_commerce.order.infrastructure.client.response.PaymentSuccessResponseOrder;
import com.live_commerce.order.infrastructure.common.ResponseUtil;
import com.live_commerce.order.infrastructure.security.RequestUserDetails;
import com.live_commerce.order.presentation.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 주문 REST API 컨트롤러 (v1)
 *
 * [헥사고날 적용 현황]
 * - 주문 조회 (단건/목록): UseCase 인터페이스 사용 (GetOrderUseCase, GetOrderListUseCase)
 * - 주문 수정: UseCase 인터페이스 사용 (UpdateOrderUseCase)
 * - 주문 삭제: UseCase 인터페이스 사용 (DeleteOrderUseCase)
 * - 주문 생성: UseCase 인터페이스 사용 (CreateOrderUseCase)
 * - 주문 상태변경/결제성공: 레거시 OrderService 위임 (전환 예정)
 */
@Slf4j
@RequestMapping("/api/v1/orders")
@RestController
@RequiredArgsConstructor
public class OrderController {

    // 레거시: updateOrderStatus, notifyPaymentSuccess 위임
    private final OrderService orderService;

    // 헥사고날 UseCase
    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;
    private final GetOrderListUseCase getOrderListUseCase;
    private final UpdateOrderUseCase updateOrderUseCase;
    private final DeleteOrderUseCase deleteOrderUseCase;

    // 주문 생성 API
    @PostMapping("")
    public ResponseEntity<ApiResponse<CreateOrderResult>> createOrder(
            @Valid @RequestBody final OrderCreateRequest request,
            @AuthenticationPrincipal RequestUserDetails userDetails) {
        UUID userId = userDetails.getUserId();
        CreateOrderCommand command = new CreateOrderCommand(
                userId,
                request.productId(),
                request.orderQuantity(),
                request.requirement(),
                request.broadcastId(),
                request.couponId()
        );
        CreateOrderResult result = createOrderUseCase.createOrder(command);
        return ResponseUtil.success(result);
    }

    // 주문 목록 조회 API (ROLE_CUSTOMER: 본인 주문 / 관리자: 전체)
    @GetMapping("")
    public ResponseEntity<ApiResponse<OrderListResult>> getOrders(
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "10") final int size,
            @RequestParam(required = false) final String sort,
            @AuthenticationPrincipal RequestUserDetails userDetails) {
        UUID userId = userDetails.getUserId();
        String role = userDetails.getAuthorities().iterator().next().getAuthority();

        Pageable pageable = buildPageable(page, size, sort);
        OrderListResult result = getOrderListUseCase.getOrders(userId, role, pageable);
        return ResponseUtil.success(result);
    }

    // 주문 단건 조회 API
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderGetResult>> getOrder(
            @PathVariable final UUID orderId,
            @AuthenticationPrincipal RequestUserDetails userDetails) {
        UUID userId = userDetails.getUserId();
        String role = userDetails.getAuthorities().iterator().next().getAuthority();

        OrderGetResult result = getOrderUseCase.getOrder(orderId, userId, role);
        return ResponseUtil.success(result);
    }

    // 주문 수정 API (수량, 요구사항, 쿠폰 변경 - PENDING 상태만 가능)
    @PatchMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderUpdateResult>> updateOrder(
            @PathVariable final UUID orderId,
            @Valid @RequestBody final UpdateOrderCommand command,
            @AuthenticationPrincipal RequestUserDetails userDetails) {
        UUID userId = userDetails.getUserId();
        String role = userDetails.getAuthorities().iterator().next().getAuthority();

        OrderUpdateResult result = updateOrderUseCase.updateOrder(orderId, command, userId, role);
        return ResponseUtil.success(result);
    }

    // 주문 상태 변경 API (레거시 - PaymentStatusTransitionService 위임)
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<ApiResponse<OrderStatusUpdateResponse>> updateOrderStatus(
            @PathVariable UUID orderId,
            @RequestBody @Valid OrderStatusUpdateRequest request,
            @AuthenticationPrincipal RequestUserDetails userDetails) {
        UUID userId = userDetails.getUserId();
        String role = userDetails.getAuthorities().iterator().next().getAuthority();

        OrderStatusUpdateResponse response = orderService.updateOrderStatus(orderId, request, userId, role);
        return ResponseUtil.success(response);
    }

    // 주문 삭제 API (소프트 삭제 - 결제 취소 없음)
    @DeleteMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderDeleteResult>> deleteOrder(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal RequestUserDetails userDetails) {
        UUID userId = userDetails.getUserId();
        String role = userDetails.getAuthorities().iterator().next().getAuthority();

        OrderDeleteResult result = deleteOrderUseCase.deleteOrder(orderId, userId, role);
        return ResponseUtil.success(result);
    }

    // 결제 성공 응답 수신 API (레거시)
    @PostMapping("/{orderId}/payment-success")
    public ResponseEntity<ApiResponse<PaymentSuccessResponseOrder>> notifyPaymentSuccess(
            @PathVariable UUID orderId,
            @RequestBody PaymentSuccessRequest request) {
        PaymentSuccessResponseOrder response = orderService.updatePaymentSuccess(orderId, request);
        return ResponseUtil.success(response);
    }

    private Pageable buildPageable(int page, int size, String sort) {
        if (sort == null || sort.isBlank()) {
            return PageRequest.of(page, size);
        }
        String[] sortParams = sort.split(",");
        List<Sort.Order> orders = new ArrayList<>();
        for (String param : sortParams) {
            String[] fieldAndDirection = param.trim().split("[- ]");
            if (fieldAndDirection.length != 2) {
                throw new IllegalArgumentException("Invalid sort parameter: expected 'field direction'");
            }
            String field = fieldAndDirection[0].trim();
            String direction = fieldAndDirection[1].trim().toUpperCase();
            orders.add(new Sort.Order(Sort.Direction.fromString(direction), field));
        }
        return PageRequest.of(page, size, Sort.by(orders));
    }
}
