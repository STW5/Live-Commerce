package com.live_commerce.product.product.adapter.out.query;

import com.live_commerce.product.product.adapter.out.persistence.ProductJpaEntity;
import com.live_commerce.product.product.adapter.out.persistence.QProductJpaEntity;
import com.live_commerce.product.product.application.dto.ProductSearchCondition;
import com.live_commerce.product.product.application.dto.SortOrder;
import com.live_commerce.product.product.domain.model.Product;
import com.live_commerce.product.product.domain.port.out.ProductQueryPort;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductQueryAdapter implements ProductQueryPort {

    private final JPAQueryFactory queryFactory;
    private final QProductJpaEntity product = QProductJpaEntity.productJpaEntity;

    @Override
    public Page<Product> search(ProductSearchCondition condition, Pageable pageable) {
        List<Product> content = queryFactory
                .selectFrom(product)
                .where(containsKeyword(condition.keyword()))
                .orderBy(getSort(condition))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch()
                .stream()
                .map(ProductJpaEntity::toDomain)
                .toList();

        Long result = queryFactory
                .select(product.count())
                .from(product)
                .where(containsKeyword(condition.keyword()))
                .fetchOne();

        long total = result != null ? result : 0L;

        return new PageImpl<>(content, pageable, total);
    }

    private BooleanExpression containsKeyword(String keyword) {
        return keyword == null ? null : product.name.containsIgnoreCase(keyword);
    }

    private OrderSpecifier<?> getSort(ProductSearchCondition condition) {
        PathBuilder<ProductJpaEntity> path = new PathBuilder<>(ProductJpaEntity.class, "productJpaEntity");

        String sortBy = condition.sortOrDefault().name();
        Order direction = condition.orderOrDefault() == SortOrder.asc ? Order.ASC : Order.DESC;

        return switch (sortBy) {
            case "price" -> new OrderSpecifier<>(direction, path.getNumber("price", Integer.class));
            case "name" -> new OrderSpecifier<>(direction, path.getString("name"));
            default -> new OrderSpecifier<>(direction, path.getDate("createdAt", LocalDateTime.class));
        };
    }
}
