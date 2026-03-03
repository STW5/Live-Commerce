package com.live_commerce.order.presentation.controller;

import com.live_commerce.common.outbox.OutboxEvent;
import com.live_commerce.common.outbox.OutboxEventRepository;
import com.live_commerce.common.outbox.OutboxStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Outbox 이벤트 관리 관리자 API
 * - Dead Letter 이벤트 조회 및 수동 재시도
 * - 상태별 통계 조회
 */
@Slf4j
@Tag(name = "Outbox Admin", description = "Outbox 이벤트 관리 (관리자 전용)")
@RestController
@RequestMapping("/api/v1/admin/outbox")
@RequiredArgsConstructor
public class OutboxAdminController {

    private final OutboxEventRepository outboxEventRepository;

    @Operation(summary = "Outbox 상태별 통계 조회")
    @GetMapping("/stats")
    public ResponseEntity<OutboxStatsResponse> getStats() {
        long pending   = outboxEventRepository.countByStatus(OutboxStatus.PENDING);
        long published = outboxEventRepository.countByStatus(OutboxStatus.PUBLISHED);
        long failed    = outboxEventRepository.countByStatus(OutboxStatus.FAILED);
        long dead      = outboxEventRepository.countDeadLetterEvents();
        return ResponseEntity.ok(new OutboxStatsResponse(pending, published, failed, dead));
    }

    @Operation(summary = "Dead Letter 이벤트 목록 조회 (retryCount >= 3)")
    @GetMapping("/failed")
    public ResponseEntity<List<OutboxEvent>> getDeadLetterEvents() {
        return ResponseEntity.ok(outboxEventRepository.findDeadLetterEvents());
    }

    @Operation(summary = "FAILED 이벤트 수동 PENDING 복귀",
               description = "ids 미전달 시 Dead Letter 전체 복귀. ids 전달 시 해당 이벤트만 복귀.")
    @PostMapping("/retry")
    public ResponseEntity<String> retryFailedEvents(@RequestBody(required = false) RetryRequest req) {
        List<OutboxEvent> targets;

        if (req == null || req.ids() == null || req.ids().isEmpty()) {
            targets = outboxEventRepository.findDeadLetterEvents();
        } else {
            targets = outboxEventRepository.findAllById(req.ids());
        }

        if (targets.isEmpty()) {
            return ResponseEntity.ok("재시도할 이벤트 없음");
        }

        targets.forEach(event -> {
            event.resetForRetry();
            outboxEventRepository.save(event);
            log.info("[OutboxAdmin] 수동 재시도 - eventId: {}, topic: {}", event.getId(), event.getTopic());
        });

        return ResponseEntity.ok("수동 재시도 완료: " + targets.size() + "건");
    }

    record OutboxStatsResponse(long pending, long published, long failed, long dead) {}

    record RetryRequest(List<UUID> ids) {}
}
