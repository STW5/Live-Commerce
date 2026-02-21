package com.live_commerce.livebroadcast.application.service;

import com.live_commerce.livebroadcast.application.dto.result.LiveBroadcastResult;
import com.live_commerce.livebroadcast.domain.port.in.SearchBroadcastUseCase;
import com.live_commerce.livebroadcast.domain.port.out.BroadcastQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SearchBroadcastService implements SearchBroadcastUseCase {

    private final BroadcastQueryPort queryPort;

    @Override
    @Transactional(readOnly = true)
    public Page<LiveBroadcastResult> searchBroadcast(String keyword, Pageable pageable) {
        int validSize = switch (pageable.getPageSize()) {
            case 30, 50 -> pageable.getPageSize();
            default -> 10;
        };
        Pageable validatedPageable = PageRequest.of(
                pageable.getPageNumber(), validSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return queryPort.searchByName(keyword, validatedPageable);
    }
}
