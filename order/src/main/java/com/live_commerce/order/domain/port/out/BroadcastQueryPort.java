package com.live_commerce.order.domain.port.out;

import java.util.UUID;

/**
 * 방송 서비스 조회 포트 (Outbound)
 * - 구현체: adapter/out/client/BroadcastFeignAdapter
 */
public interface BroadcastQueryPort {
    /**
     * 방송이 현재 LIVE 상태인지 확인
     */
    boolean isLive(UUID broadcastId);
}
