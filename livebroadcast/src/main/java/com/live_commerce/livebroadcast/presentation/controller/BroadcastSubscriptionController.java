package com.live_commerce.livebroadcast.presentation.controller;

import com.live_commerce.livebroadcast.application.dto.request.CreateSubscriptionRequestDto;
import com.live_commerce.livebroadcast.application.dto.response.SubscriptionResponseDto;
import com.live_commerce.livebroadcast.application.dto.result.BroadcastSubscriptionResult;
import com.live_commerce.livebroadcast.domain.port.in.GetMySubscriptionsUseCase;
import com.live_commerce.livebroadcast.domain.port.in.RegisterBroadcastAlarmUseCase;
import com.live_commerce.livebroadcast.domain.port.in.SubscribeBroadcastUseCase;
import com.live_commerce.livebroadcast.domain.port.in.UnsubscribeBroadcastUseCase;
import com.live_commerce.livebroadcast.infrastructure.common.ResponseUtil;
import com.live_commerce.livebroadcast.infrastructure.security.RequestUserDetails;
import com.live_commerce.livebroadcast.presentation.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/livebroadcasts/subscriptions")
@RequiredArgsConstructor
public class BroadcastSubscriptionController {

    private final SubscribeBroadcastUseCase subscribeUseCase;
    private final UnsubscribeBroadcastUseCase unsubscribeUseCase;
    private final GetMySubscriptionsUseCase getMySubscriptionsUseCase;
    private final RegisterBroadcastAlarmUseCase registerAlarmUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<SubscriptionResponseDto>> subscribe(
            @AuthenticationPrincipal RequestUserDetails userDetails,
            @RequestBody CreateSubscriptionRequestDto request) {
        BroadcastSubscriptionResult result = subscribeUseCase.subscribe(
                userDetails.getUserId(), request.broadcastId());
        return ResponseUtil.success(toResponseDto(result));
    }

    @DeleteMapping("/{broadcastId}")
    public ResponseEntity<ApiResponse<String>> unsubscribe(
            @AuthenticationPrincipal RequestUserDetails userDetails,
            @PathVariable UUID broadcastId) {
        unsubscribeUseCase.unsubscribe(userDetails.getUserId(), broadcastId);
        return ResponseUtil.success("구독이 취소되었습니다.");
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SubscriptionResponseDto>>> getMySubscriptions(
            @AuthenticationPrincipal RequestUserDetails userDetails) {
        List<BroadcastSubscriptionResult> results = getMySubscriptionsUseCase.getMySubscriptions(userDetails.getUserId());
        List<SubscriptionResponseDto> response = results.stream().map(this::toResponseDto).toList();
        return ResponseUtil.success(response);
    }

    @PostMapping("/alarm")
    public ResponseEntity<ApiResponse<String>> registerAlarm(
            @AuthenticationPrincipal RequestUserDetails userDetails,
            @RequestBody CreateSubscriptionRequestDto request) {
        registerAlarmUseCase.registerAlarm(userDetails.getUserId(), request.broadcastId());
        return ResponseUtil.success("알림이 등록되었습니다.");
    }

    private SubscriptionResponseDto toResponseDto(BroadcastSubscriptionResult r) {
        return new SubscriptionResponseDto(r.subscriptionId(), r.userId(), r.broadcastId(), null);
    }
}
