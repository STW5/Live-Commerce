package com.live_commerce.payment.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.live_commerce.common.outbox.OutboxEvent;
import com.live_commerce.common.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Payment 서비스 Outbox 이벤트 저장 헬퍼
 * - 호출자의 @Transactional 컨텍스트에 참여하여 원자적 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventHelper {

    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public void saveEvent(String aggregateType, UUID aggregateId,
                          String eventType, String topic, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            OutboxEvent outboxEvent = OutboxEvent.create(aggregateType, aggregateId, eventType, topic, json);
            outboxEventRepository.save(outboxEvent);
            log.debug("[OutboxHelper] 이벤트 저장 - type: {}, aggregateId: {}, topic: {}",
                    eventType, aggregateId, topic);
        } catch (JsonProcessingException e) {
            log.error("[OutboxHelper] 이벤트 직렬화 실패 - type: {}, error: {}", eventType, e.getMessage());
            throw new RuntimeException("Outbox 이벤트 직렬화 실패: " + eventType, e);
        }
    }
}
