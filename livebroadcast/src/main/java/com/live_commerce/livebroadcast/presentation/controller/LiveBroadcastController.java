package com.live_commerce.livebroadcast.presentation.controller;

import com.live_commerce.livebroadcast.application.dto.command.CreateBroadcastCommand;
import com.live_commerce.livebroadcast.application.dto.command.UpdateBroadcastCommand;
import com.live_commerce.livebroadcast.application.dto.request.LiveBroadcastCreateRequestDto;
import com.live_commerce.livebroadcast.application.dto.request.LiveBroadcastUpdateRequestDto;
import com.live_commerce.livebroadcast.application.dto.response.LiveBroadcastResponseDto;
import com.live_commerce.livebroadcast.application.dto.result.LiveBroadcastResult;
import com.live_commerce.livebroadcast.domain.port.in.CreateBroadcastUseCase;
import com.live_commerce.livebroadcast.domain.port.in.DeleteBroadcastUseCase;
import com.live_commerce.livebroadcast.domain.port.in.GetBroadcastSubscribersUseCase;
import com.live_commerce.livebroadcast.domain.port.in.GetBroadcastUseCase;
import com.live_commerce.livebroadcast.domain.port.in.SearchBroadcastUseCase;
import com.live_commerce.livebroadcast.domain.port.in.UpdateBroadcastUseCase;
import com.live_commerce.livebroadcast.infrastructure.common.ResponseUtil;
import com.live_commerce.livebroadcast.infrastructure.security.RequestUserDetails;
import com.live_commerce.livebroadcast.presentation.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RefreshScope
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/livebroadcasts")
public class LiveBroadcastController {

    private final CreateBroadcastUseCase createBroadcastUseCase;
    private final UpdateBroadcastUseCase updateBroadcastUseCase;
    private final DeleteBroadcastUseCase deleteBroadcastUseCase;
    private final GetBroadcastUseCase getBroadcastUseCase;
    private final SearchBroadcastUseCase searchBroadcastUseCase;
    private final GetBroadcastSubscribersUseCase getSubscribersUseCase;

    @PreAuthorize("hasAnyRole('MASTER', 'SHOW_HOST')")
    @PostMapping
    public ResponseEntity<ApiResponse<LiveBroadcastResponseDto>> createBroadcast(
            @RequestBody @Valid LiveBroadcastCreateRequestDto requestDto,
            @AuthenticationPrincipal RequestUserDetails userDetails) {
        CreateBroadcastCommand command = new CreateBroadcastCommand(
                requestDto.broadcastName(), requestDto.startTime(), requestDto.endTime());
        LiveBroadcastResult result = createBroadcastUseCase.createBroadcast(
                command, userDetails.getUserId(), requestDto.companyId());
        return ResponseUtil.success(toResponseDto(result));
    }

    @PreAuthorize("hasAnyRole('MASTER','SHOW_HOST','SELLER','CUSTOMER')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LiveBroadcastResponseDto>> getBroadcast(@PathVariable UUID id) {
        LiveBroadcastResult result = getBroadcastUseCase.getBroadcast(id);
        return ResponseUtil.success(toResponseDto(result));
    }

    @PreAuthorize("hasAnyRole('MASTER','SHOW_HOST')")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<LiveBroadcastResponseDto>> updateBroadcast(
            @PathVariable UUID id,
            @RequestBody LiveBroadcastUpdateRequestDto requestDto,
            @AuthenticationPrincipal RequestUserDetails userDetails) {
        UpdateBroadcastCommand command = new UpdateBroadcastCommand(
                requestDto.broadcastName(), requestDto.startTime(),
                requestDto.endTime(), requestDto.broadcastStatus());
        LiveBroadcastResult result = updateBroadcastUseCase.updateBroadcast(
                id, command, userDetails.getUserId(), getRole(userDetails));
        return ResponseUtil.success(toResponseDto(result));
    }

    @PreAuthorize("hasAnyRole('MASTER','SHOW_HOST')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteBroadcast(
            @PathVariable UUID id,
            @AuthenticationPrincipal RequestUserDetails userDetails) {
        deleteBroadcastUseCase.deleteBroadcast(id, userDetails.getUserId(), getRole(userDetails));
        return ResponseUtil.success("라이브 방송이 삭제되었습니다.");
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<LiveBroadcastResponseDto>>> searchBroadcast(
            @RequestParam(required = false) String keyword, Pageable pageable) {
        Page<LiveBroadcastResult> results = searchBroadcastUseCase.searchBroadcast(keyword, pageable);
        return ResponseUtil.success(results.map(this::toResponseDto));
    }

    @PreAuthorize("hasRole('MASTER')")
    @GetMapping("/{broadcastId}/subscribers")
    public ResponseEntity<ApiResponse<Page<UUID>>> getSubscribers(
            @PathVariable UUID broadcastId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        if (size != 100) size = 100;
        Pageable pageable = PageRequest.of(page, size);
        return ResponseUtil.success(getSubscribersUseCase.getSubscribers(broadcastId, pageable));
    }

    private LiveBroadcastResponseDto toResponseDto(LiveBroadcastResult r) {
        return new LiveBroadcastResponseDto(
                r.liveBroadcastId(), r.broadcastName(), r.startTime(), r.endTime(),
                r.broadcastStatus(), r.totalViewerCount(), r.hostId(), r.companyId());
    }

    private String getRole(RequestUserDetails userDetails) {
        return userDetails.getAuthorities().iterator().next().getAuthority();
    }
}
