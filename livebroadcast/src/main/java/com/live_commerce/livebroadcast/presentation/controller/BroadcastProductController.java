package com.live_commerce.livebroadcast.presentation.controller;

import com.live_commerce.livebroadcast.application.dto.request.BroadcastProductConnectDto;
import com.live_commerce.livebroadcast.application.dto.response.BroadcastProductResponseDto;
import com.live_commerce.livebroadcast.application.dto.result.BroadcastProductResult;
import com.live_commerce.livebroadcast.domain.port.in.CheckBroadcastProductExistsUseCase;
import com.live_commerce.livebroadcast.domain.port.in.ConnectBroadcastProductUseCase;
import com.live_commerce.livebroadcast.domain.port.in.DisconnectBroadcastProductUseCase;
import com.live_commerce.livebroadcast.domain.port.in.GetBroadcastProductsUseCase;
import com.live_commerce.livebroadcast.infrastructure.common.ResponseUtil;
import com.live_commerce.livebroadcast.infrastructure.security.RequestUserDetails;
import com.live_commerce.livebroadcast.presentation.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/livebroadcasts/{broadcastId}/products")
@RequiredArgsConstructor
public class BroadcastProductController {

    private final ConnectBroadcastProductUseCase connectProductUseCase;
    private final DisconnectBroadcastProductUseCase disconnectProductUseCase;
    private final GetBroadcastProductsUseCase getProductsUseCase;
    private final CheckBroadcastProductExistsUseCase checkExistsUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<BroadcastProductResponseDto>> connectBroadcastProduct(
            @RequestBody BroadcastProductConnectDto requestDto,
            @PathVariable UUID broadcastId,
            @AuthenticationPrincipal RequestUserDetails userDetails) {
        BroadcastProductResult result = connectProductUseCase.connectProduct(
                broadcastId, requestDto.productId(), userDetails.getUserId(), getRole(userDetails));
        return ResponseUtil.success(toResponseDto(result));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<String>> disconnectBroadcastProduct(
            @PathVariable UUID productId,
            @PathVariable UUID broadcastId,
            @AuthenticationPrincipal RequestUserDetails userDetails) {
        disconnectProductUseCase.disconnectProduct(
                broadcastId, productId, userDetails.getUserId(), getRole(userDetails));
        return ResponseUtil.success("해당 방송과 연결된 상품을 해제하였습니다.");
    }

    @GetMapping("")
    public ResponseEntity<ApiResponse<Page<BroadcastProductResponseDto>>> getProducts(
            @PathVariable UUID broadcastId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<BroadcastProductResult> results = getProductsUseCase.getProducts(broadcastId, pageable);
        return ResponseUtil.success(results.map(this::toResponseDto));
    }

    @GetMapping("/{productId}/exists")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkProductExists(
            @PathVariable UUID broadcastId,
            @PathVariable UUID productId) {
        boolean exists = checkExistsUseCase.exists(broadcastId, productId);
        return ResponseUtil.success(Map.of("exists", exists));
    }

    private BroadcastProductResponseDto toResponseDto(BroadcastProductResult r) {
        return new BroadcastProductResponseDto(r.broadcastProductId(), r.liveBroadcastId(), r.productId());
    }

    private String getRole(RequestUserDetails userDetails) {
        return userDetails.getAuthorities().iterator().next().getAuthority();
    }
}
