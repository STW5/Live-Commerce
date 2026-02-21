package com.live_commerce.livebroadcast.adapter.out.persistence.query;

import com.live_commerce.livebroadcast.adapter.out.persistence.entity.QBroadcastProductJpaEntity;
import com.live_commerce.livebroadcast.adapter.out.persistence.entity.QLiveBroadcastJpaEntity;
import com.live_commerce.livebroadcast.application.dto.result.LiveBroadcastResult;
import com.live_commerce.livebroadcast.domain.port.out.BroadcastQueryPort;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BroadcastQueryAdapter implements BroadcastQueryPort {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<LiveBroadcastResult> searchByName(String keyword, Pageable pageable) {
        QLiveBroadcastJpaEntity b = QLiveBroadcastJpaEntity.liveBroadcastJpaEntity;
        BooleanExpression notDeleted = b.deletedStatus.isFalse();
        BooleanExpression keywordCondition = StringUtils.hasText(keyword)
                ? b.broadcastName.containsIgnoreCase(keyword)
                : null;

        List<LiveBroadcastResult> content = queryFactory
                .selectFrom(b)
                .where(notDeleted, keywordCondition)
                .orderBy(b.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch()
                .stream()
                .map(e -> e.toDomain())
                .map(LiveBroadcastResult::from)
                .toList();

        Long total = Optional.ofNullable(
                queryFactory.select(b.count()).from(b).where(notDeleted, keywordCondition).fetchOne()
        ).orElse(0L);

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<UUID> findProductIdsByBroadcastId(UUID broadcastId, Pageable pageable) {
        QBroadcastProductJpaEntity bp = QBroadcastProductJpaEntity.broadcastProductJpaEntity;

        List<UUID> productIds = queryFactory
                .select(bp.productId)
                .from(bp)
                .where(bp.liveBroadcastId.eq(broadcastId), bp.deletedStatus.isFalse())
                .orderBy(new OrderSpecifier<>(Order.DESC, bp.createdAt))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = Optional.ofNullable(
                queryFactory.select(bp.count()).from(bp)
                        .where(bp.liveBroadcastId.eq(broadcastId), bp.deletedStatus.isFalse())
                        .fetchOne()
        ).orElse(0L);

        return new PageImpl<>(productIds, pageable, total);
    }
}
