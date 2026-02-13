-- =====================================================
-- V2: Outbox Pattern + Saga State 테이블 추가
-- orders 스키마에 생성
-- =====================================================

SET search_path TO orders;

-- =====================================================
-- 1. Outbox Events 테이블
-- 이벤트 유실 방지를 위한 Transactional Outbox Pattern
-- =====================================================
CREATE TABLE IF NOT EXISTS outbox_event (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(50)  NOT NULL,                      -- 집합체 타입 ('ORDER')
    aggregate_id    UUID         NOT NULL,                      -- orderId
    event_type      VARCHAR(100) NOT NULL,                      -- 이벤트 타입 ('INVENTORY_ROLLBACK', 'ORDER_FAILED')
    topic           VARCHAR(100) NOT NULL,                      -- Kafka 토픽
    payload         TEXT         NOT NULL,                      -- JSON 직렬화 이벤트 데이터
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',    -- PENDING, PUBLISHED, FAILED
    retry_count     INT          NOT NULL DEFAULT 0,
    error_message   TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbox_status     ON outbox_event(status);
CREATE INDEX IF NOT EXISTS idx_outbox_created_at ON outbox_event(created_at);

COMMENT ON TABLE outbox_event IS 'Transactional Outbox Pattern - Kafka 이벤트 유실 방지';
COMMENT ON COLUMN outbox_event.status IS 'PENDING: 발행 대기, PUBLISHED: 발행 완료, FAILED: 발행 실패';

-- =====================================================
-- 2. Saga State 테이블
-- 분산 트랜잭션 진행 상태 추적
-- =====================================================
CREATE TABLE IF NOT EXISTS saga_state (
    saga_id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_type       VARCHAR(50)  NOT NULL,                      -- Saga 유형 ('ORDER_CREATION')
    aggregate_id    UUID         NOT NULL UNIQUE,               -- orderId (1주문 = 1 Saga)
    status          VARCHAR(20)  NOT NULL,                      -- STARTED, RUNNING, COMPLETED, FAILED, COMPENSATING, COMPENSATED
    current_step    VARCHAR(50)  NOT NULL DEFAULT 'INIT',       -- 현재 처리 단계
    payload         TEXT,                                       -- 추가 컨텍스트 JSON
    error_message   TEXT,
    retry_count     INT          NOT NULL DEFAULT 0,
    started_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_saga_aggregate_id ON saga_state(aggregate_id);
CREATE INDEX IF NOT EXISTS idx_saga_status       ON saga_state(status);

COMMENT ON TABLE saga_state IS 'Saga 분산 트랜잭션 상태 추적';
COMMENT ON COLUMN saga_state.status IS 'STARTED: 시작, RUNNING: 실행중, COMPLETED: 완료, FAILED: 실패, COMPENSATING: 보상중, COMPENSATED: 보상완료';
COMMENT ON COLUMN saga_state.current_step IS 'INIT, INVENTORY_DECREASING, INVENTORY_DECREASED, PAYMENT_READY, PAYMENT_DONE';
