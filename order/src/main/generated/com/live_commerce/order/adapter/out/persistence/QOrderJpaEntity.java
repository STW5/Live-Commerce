package com.live_commerce.order.adapter.out.persistence;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QOrderJpaEntity is a Querydsl query type for OrderJpaEntity
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QOrderJpaEntity extends EntityPathBase<OrderJpaEntity> {

    private static final long serialVersionUID = -1714282325L;

    public static final QOrderJpaEntity orderJpaEntity = new QOrderJpaEntity("orderJpaEntity");

    public final QBaseJpaEntity _super = new QBaseJpaEntity(this);

    public final ComparablePath<java.util.UUID> broadcastId = createComparable("broadcastId", java.util.UUID.class);

    public final ComparablePath<java.util.UUID> couponId = createComparable("couponId", java.util.UUID.class);

    //inherited
    public final DateTimePath<java.time.LocalDateTime> createdAt = _super.createdAt;

    //inherited
    public final StringPath createdBy = _super.createdBy;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> deletedAt = _super.deletedAt;

    //inherited
    public final StringPath deletedBy = _super.deletedBy;

    //inherited
    public final BooleanPath deletedStatus = _super.deletedStatus;

    public final NumberPath<Double> finalPaidPrice = createNumber("finalPaidPrice", Double.class);

    public final ComparablePath<java.util.UUID> id = createComparable("id", java.util.UUID.class);

    public final ComparablePath<java.util.UUID> productId = createComparable("productId", java.util.UUID.class);

    public final NumberPath<Integer> productQuantity = createNumber("productQuantity", Integer.class);

    public final NumberPath<Double> productTotalPrice = createNumber("productTotalPrice", Double.class);

    public final StringPath requirement = createString("requirement");

    public final EnumPath<com.live_commerce.order.domain.model.OrderStatus> status = createEnum("status", com.live_commerce.order.domain.model.OrderStatus.class);

    //inherited
    public final DateTimePath<java.time.LocalDateTime> updatedAt = _super.updatedAt;

    //inherited
    public final StringPath updatedBy = _super.updatedBy;

    public final ComparablePath<java.util.UUID> userId = createComparable("userId", java.util.UUID.class);

    public QOrderJpaEntity(String variable) {
        super(OrderJpaEntity.class, forVariable(variable));
    }

    public QOrderJpaEntity(Path<? extends OrderJpaEntity> path) {
        super(path.getType(), path.getMetadata());
    }

    public QOrderJpaEntity(PathMetadata metadata) {
        super(OrderJpaEntity.class, metadata);
    }

}

