package com.live_commerce.livebroadcast.domain.port.in;

import com.live_commerce.livebroadcast.application.dto.result.LiveBroadcastResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SearchBroadcastUseCase {
    Page<LiveBroadcastResult> searchBroadcast(String keyword, Pageable pageable);
}
