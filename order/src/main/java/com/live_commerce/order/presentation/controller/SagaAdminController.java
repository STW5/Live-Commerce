package com.live_commerce.order.presentation.controller;

import com.live_commerce.common.saga.SagaState;
import com.live_commerce.common.saga.SagaStateRepository;
import com.live_commerce.common.saga.SagaStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Saga 상태 관리 관리자 API
 * - 분산 트랜잭션 진행 상태 조회
 * - 보상 트랜잭션 완료 처리 (수동)
 */
@Tag(name = "Saga Admin", description = "분산 트랜잭션 Saga 상태 관리 (관리자 전용)")
@RestController
@RequestMapping("/api/v1/admin/saga")
@RequiredArgsConstructor
public class SagaAdminController {

    private final SagaStateRepository sagaStateRepository;

    @Operation(summary = "주문 Saga 상태 조회")
    @GetMapping("/{orderId}")
    public ResponseEntity<SagaState> getSagaState(@PathVariable UUID orderId) {
        return sagaStateRepository.findByAggregateId(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "보상 완료 처리 (수동)")
    @PostMapping("/{orderId}/compensated")
    public ResponseEntity<String> markCompensated(@PathVariable UUID orderId) {
        sagaStateRepository.findByAggregateId(orderId).ifPresent(saga -> {
            saga.completeCompensation();
            sagaStateRepository.save(saga);
        });
        return ResponseEntity.ok("보상 트랜잭션 완료 처리됨");
    }

    @Operation(summary = "실패 중인 Saga 목록 조회")
    @GetMapping("/failed")
    public ResponseEntity<List<SagaState>> getFailedSagas() {
        return ResponseEntity.ok(sagaStateRepository.findByStatus(SagaStatus.FAILED));
    }

    @Operation(summary = "보상 중인 Saga 목록 조회")
    @GetMapping("/compensating")
    public ResponseEntity<List<SagaState>> getCompensatingSagas() {
        return ResponseEntity.ok(sagaStateRepository.findByStatus(SagaStatus.COMPENSATING));
    }
}
