package com.live_commerce.order.adapter.out.persistence;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QBaseJpaEntity is a Querydsl query type for BaseJpaEntity
 */
@Generated("com.querydsl.codegen.DefaultSupertypeSerializer")
public class QBaseJpaEntity extends EntityPathBase<BaseJpaEntity> {

    private static final long serialVersionUID = -2246734L;

    public static final QBaseJpaEntity baseJpaEntity = new QBaseJpaEntity("baseJpaEntity");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final StringPath createdBy = createString("createdBy");

    public final DateTimePath<java.time.LocalDateTime> deletedAt = createDateTime("deletedAt", java.time.LocalDateTime.class);

    public final StringPath deletedBy = createString("deletedBy");

    public final BooleanPath deletedStatus = createBoolean("deletedStatus");

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public final StringPath updatedBy = createString("updatedBy");

    public QBaseJpaEntity(String variable) {
        super(BaseJpaEntity.class, forVariable(variable));
    }

    public QBaseJpaEntity(Path<? extends BaseJpaEntity> path) {
        super(path.getType(), path.getMetadata());
    }

    public QBaseJpaEntity(PathMetadata metadata) {
        super(BaseJpaEntity.class, metadata);
    }

}

