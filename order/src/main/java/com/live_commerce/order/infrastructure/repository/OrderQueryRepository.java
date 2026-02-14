package com.live_commerce.order.infrastructure.repository;

import com.live_commerce.order.adapter.out.persistence.OrderJpaEntity;
import com.live_commerce.order.adapter.out.persistence.QOrderJpaEntity;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * QueryDSL 기반 Order 조회 레포지토리
 *
 * [아키텍처 노트]
 * - Order가 @Entity에서 순수 도메인으로 변경됨에 따라 QOrder → QOrderJpaEntity 사용
 * - OrderJpaEntity를 직접 반환 (레거시 OrderService가 OrderRepository를 통해 Order로 변환)
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OrderQueryRepository {

    private final JPAQueryFactory queryFactory;
    private final QOrderJpaEntity orderJpa = QOrderJpaEntity.orderJpaEntity;

    /**
     * 주문 전체 조회 (삭제되지 않은 데이터만)
     */
    public Page<OrderJpaEntity> findAll(Pageable pageable) {
        List<OrderJpaEntity> orders = queryFactory
                .selectFrom(orderJpa)
                .where(orderJpa.deletedAt.isNull())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        log.info("Fetched Orders: " + orders);

        long total = queryFactory
                .select(orderJpa.count())
                .from(orderJpa)
                .where(orderJpa.deletedAt.isNull())
                .fetchFirst();

        return new PageImpl<>(orders, pageable, total);
    }
}
