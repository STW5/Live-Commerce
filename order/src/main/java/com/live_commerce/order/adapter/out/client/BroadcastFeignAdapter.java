package com.live_commerce.order.adapter.out.client;

import com.live_commerce.order.domain.port.out.BroadcastQueryPort;
import com.live_commerce.order.infrastructure.client.feign.BroadcastClient;
import com.live_commerce.order.infrastructure.client.feignEnum.BroadcastStatus;
import com.live_commerce.order.infrastructure.client.response.BroadcastStatusResponse;
import com.live_commerce.order.presentation.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * BroadcastQueryPort 구현체
 * - Feign 클라이언트를 래핑하여 Domain이 인프라에 의존하지 않도록 격리
 * - Feign 응답 DTO → Domain이 이해할 수 있는 형태로 변환
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BroadcastFeignAdapter implements BroadcastQueryPort {

    private final BroadcastClient broadcastClient;

    @Override
    public boolean isLive(UUID broadcastId) {
        try {
            ApiResponse<BroadcastStatusResponse> response = broadcastClient.getBroadcast(broadcastId);
            BroadcastStatusResponse statusResponse = response.getData();
            return statusResponse != null
                    && statusResponse.getBroadcastStatus() == BroadcastStatus.LIVE;
        } catch (Exception e) {
            log.warn("[BroadcastFeignAdapter] 방송 상태 조회 실패 - broadcastId: {}, error: {}",
                    broadcastId, e.getMessage());
            return false;
        }
    }
}
