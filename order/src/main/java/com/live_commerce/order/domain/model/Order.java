package com.live_commerce.order.domain.model;

import com.live_commerce.order.domain.exception.OrderDomainException;
import com.live_commerce.order.domain.model.vo.DiscountPolicy;
import com.live_commerce.order.domain.model.vo.Money;
import com.live_commerce.order.domain.model.vo.OrderQuantity;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Order Aggregate Root - 순수 도메인 모델
 *
 * [아키텍처 노트]
 * - @Entity, @Table, @Column 등 JPA 어노테이션 완전 제거 (도메인 순수화)
 * - Lombok 제거, 명시적 getter 사용
 * - Value Object 필드: Money (totalPrice, finalPrice), OrderQuantity (quantity)
 * - 생성은 정적 팩토리 메서드(create / reconstitute) 사용
 * - JPA 매핑은 adapter/out/persistence/OrderJpaEntity 가 담당
 * - 감사 필드(updatedAt, deletedAt 등)는 OrderJpaEntity(BaseJpaEntity)가 관리
 */
public class Order {

    private UUID id;
    private UUID userId;
    private UUID productId;
    private UUID broadcastId;
    private UUID couponId;
    private OrderQuantity quantity;
    private Money totalPrice;
    private Money finalPrice;
    private String requirement;
    private OrderStatus status;
    private LocalDateTime createdAt;

    // Mapper 전용 기본 생성자 (package-private)
    protected Order() {}

    // 전체 필드 생성자 (reconstitute / create 전용)
    private Order(UUID id, UUID userId, UUID productId, UUID broadcastId, UUID couponId,
                  OrderQuantity quantity, Money totalPrice, Money finalPrice,
                  String requirement, OrderStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.productId = productId;
        this.broadcastId = broadcastId;
        this.couponId = couponId;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.finalPrice = finalPrice;
        this.requirement = requirement;
        this.status = status;
        this.createdAt = createdAt;
    }

    // -----------------------------------------------------------------------
    // 정적 팩토리 메서드
    // -----------------------------------------------------------------------

    /**
     * 신규 주문 생성 팩토리 메서드
     * - 할인 계산 로직 포함
     * - ID는 영속화 시 할당 (null로 초기화)
     */
    public static Order create(UUID userId, UUID productId, UUID broadcastId,
                               OrderQuantity quantity, Money unitPrice,
                               UUID couponId, DiscountPolicy discountPolicy) {
        Money totalPrice = unitPrice.multiply(quantity.value());
        Money finalPrice = (discountPolicy != null)
                ? discountPolicy.apply(totalPrice)
                : totalPrice;

        return new Order(
                null,
                userId, productId, broadcastId, couponId,
                quantity, totalPrice, finalPrice,
                null,
                OrderStatus.PENDING,
                LocalDateTime.now()
        );
    }

    /**
     * DB 재구성 팩토리 메서드 (OrderMapper 전용)
     * - 유효성 검사 skip (이미 저장된 데이터)
     */
    public static Order reconstitute(UUID id, UUID userId, UUID productId, UUID broadcastId,
                                     UUID couponId, OrderQuantity quantity,
                                     Money totalPrice, Money finalPrice,
                                     String requirement, OrderStatus status,
                                     LocalDateTime createdAt) {
        return new Order(id, userId, productId, broadcastId, couponId,
                quantity, totalPrice, finalPrice, requirement, status, createdAt);
    }

    // -----------------------------------------------------------------------
    // 비즈니스 메서드
    // -----------------------------------------------------------------------

    /**
     * 주문 상태 변경 - 도메인 예외 사용
     */
    public void changeStatus(OrderStatus newStatus) {
        if (this.status == OrderStatus.CANCELLED) {
            throw new OrderDomainException("이미 취소된 주문은 상태를 변경할 수 없습니다.");
        }
        if (this.status == newStatus) {
            throw new OrderDomainException("변경하려는 상태가 현재 상태와 같습니다.");
        }
        this.status = newStatus;
    }

    /**
     * 결제 확정 (PAID 상태로 변경)
     */
    public void confirmPayment() {
        changeStatus(OrderStatus.PAID);
    }

    /**
     * 주문 실패 처리 (FAILED 상태로 변경)
     */
    public void failOrder() {
        changeStatus(OrderStatus.FAILED);
    }

    /**
     * 주문 취소 - 결제 완료/처리 중인 주문은 취소 불가
     */
    public void cancel() {
        if (this.status == OrderStatus.PAID || this.status == OrderStatus.PROCESSING) {
            throw new OrderDomainException("결제 완료 또는 처리 중인 주문은 취소할 수 없습니다.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    /**
     * ID 주입 (영속화 후 어댑터가 사용)
     */
    public void assignId(UUID id) {
        if (this.id != null) {
            throw new OrderDomainException("이미 ID가 할당된 주문입니다.");
        }
        this.id = id;
    }

    /**
     * 요구사항 설정 (create() 이후 선택적으로 설정 가능)
     */
    public void setRequirement(String requirement) {
        this.requirement = requirement;
    }

    /**
     * 주문 수정 (PENDING 상태에서만 허용 - 호출 측에서 상태 검증)
     * 레거시 updateOrder(Order) 대신 명시적 파라미터로 업데이트
     */
    public void applyUpdate(Integer productQuantity, Double productTotalPrice,
                             Double finalPaidPrice, String requirement, UUID couponId) {
        if (productQuantity != null) {
            this.quantity = new OrderQuantity(productQuantity);
        }
        if (productTotalPrice != null) {
            this.totalPrice = Money.of(productTotalPrice);
        }
        if (finalPaidPrice != null) {
            this.finalPrice = Money.of(finalPaidPrice);
        }
        if (requirement != null) {
            this.requirement = requirement;
        }
        if (couponId != null) {
            this.couponId = couponId;
        }
    }

    // -----------------------------------------------------------------------
    // Getters (명시적)
    // -----------------------------------------------------------------------

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getProductId() { return productId; }
    public UUID getBroadcastId() { return broadcastId; }
    public UUID getCouponId() { return couponId; }
    public OrderQuantity getQuantity() { return quantity; }
    public Money getTotalPrice() { return totalPrice; }
    public Money getFinalPrice() { return finalPrice; }
    public String getRequirement() { return requirement; }
    public OrderStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // -----------------------------------------------------------------------
    // 하위 호환 getter (레거시 서비스 코드 지원)
    // -----------------------------------------------------------------------

    /**
     * @deprecated quantity VO 사용: getQuantity().value()
     */
    @Deprecated
    public Integer getProductQuantity() {
        return quantity != null ? quantity.value() : null;
    }

    /**
     * @deprecated totalPrice VO 사용: getTotalPrice().toDouble()
     */
    @Deprecated
    public Double getProductTotalPrice() {
        return totalPrice != null ? totalPrice.toDouble() : null;
    }

    /**
     * @deprecated finalPrice VO 사용: getFinalPrice().toDouble()
     */
    @Deprecated
    public Double getFinalPaidPrice() {
        return finalPrice != null ? finalPrice.toDouble() : null;
    }

    /**
     * @deprecated createdAt 사용: getCreatedAt()
     * updatedAt은 OrderJpaEntity(BaseJpaEntity)가 관리 → null 반환
     */
    @Deprecated
    public LocalDateTime getUpdatedAt() {
        return null;
    }
}
