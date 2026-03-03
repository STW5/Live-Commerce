package com.live_commerce.product.inventory.presentation.controller;

import com.live_commerce.product.inventory.application.dto.request.InventoryCreateRequestDto;
import com.live_commerce.product.inventory.application.dto.request.InventoryDecreaseRequestDto;
import com.live_commerce.product.inventory.application.dto.request.InventoryIncreaseRequestDto;
import com.live_commerce.product.inventory.application.dto.response.InventoryCheckOrderableResponseDto;
import com.live_commerce.product.inventory.application.dto.response.InventoryCheckQuantityResponseDto;
import com.live_commerce.product.inventory.application.dto.response.InventoryResponseDto;
import com.live_commerce.product.inventory.application.dto.result.InventoryOrderableResult;
import com.live_commerce.product.inventory.application.dto.result.InventoryQuantityResult;
import com.live_commerce.product.inventory.application.dto.result.InventoryResult;
import com.live_commerce.product.inventory.domain.port.in.*;
import com.live_commerce.product.inventory.infrastructure.common.ResponseUtil;
import com.live_commerce.product.inventory.presentation.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventories")
@RequiredArgsConstructor
public class InventoryController {

    private final CreateInventoryUseCase createInventoryUseCase;
    private final GetInventoryUseCase getInventoryUseCase;
    private final DecreaseInventoryUseCase decreaseInventoryUseCase;
    private final IncreaseInventoryUseCase increaseInventoryUseCase;
    private final CheckInventoryQuantityUseCase checkInventoryQuantityUseCase;
    private final CheckOrderableInventoryUseCase checkOrderableInventoryUseCase;

    @PreAuthorize("hasAnyRole('MASTER', 'SELLER')")
    @PostMapping
    public ResponseEntity<ApiResponse<InventoryResponseDto>> createInventory(
            @RequestBody InventoryCreateRequestDto requestDto) {
        InventoryResult result = createInventoryUseCase.createInventory(requestDto);
        return ResponseUtil.success(toResponseDto(result));
    }

    @GetMapping("/by-id/{inventoryId}")
    public ResponseEntity<ApiResponse<InventoryResponseDto>> getInventoryById(
            @PathVariable("inventoryId") UUID inventoryId) {
        InventoryResult result = getInventoryUseCase.getInventory(inventoryId);
        return ResponseUtil.success(toResponseDto(result));
    }

    @PostMapping("/decrease")
    public ResponseEntity<ApiResponse<String>> decreaseInventory(
            @Valid @RequestBody InventoryDecreaseRequestDto requestDto) {
        decreaseInventoryUseCase.decreaseInventory(requestDto.productId(), requestDto.quantity());
        return ResponseUtil.success("재고가 차감되었습니다.");
    }

    @PostMapping("/increase")
    public ResponseEntity<ApiResponse<String>> increaseInventory(
            @Valid @RequestBody InventoryIncreaseRequestDto requestDto) {
        increaseInventoryUseCase.increaseInventory(requestDto.productId(), requestDto.quantity());
        return ResponseUtil.success("재고가 복원되었습니다.");
    }

    @GetMapping("/check-quantity")
    public ResponseEntity<ApiResponse<InventoryCheckQuantityResponseDto>> checkInventoryQuantity(
            @RequestParam UUID productId) {
        InventoryQuantityResult result = checkInventoryQuantityUseCase.checkInventoryQuantity(productId);
        return ResponseUtil.success(new InventoryCheckQuantityResponseDto(result.availableQuantity()));
    }

    @GetMapping("/check-orderable")
    public ResponseEntity<ApiResponse<InventoryCheckOrderableResponseDto>> checkOrderableInventory(
            @RequestParam UUID productId, @RequestParam int orderQuantity) {
        InventoryOrderableResult result = checkOrderableInventoryUseCase.checkOrderableInventory(productId, orderQuantity);
        return ResponseUtil.success(new InventoryCheckOrderableResponseDto(result.orderable()));
    }

    private InventoryResponseDto toResponseDto(InventoryResult r) {
        return new InventoryResponseDto(
                r.inventoryId(), r.productId(), r.quantity(),
                r.reservedQuantity(), r.availableQuantity(), r.inventoryStatus());
    }
}
