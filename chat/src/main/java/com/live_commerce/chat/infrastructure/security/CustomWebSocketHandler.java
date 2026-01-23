package com.live_commerce.chat.infrastructure.security;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.live_commerce.chat.application.dto.request.ChatCreateRequest;
import com.live_commerce.chat.application.exception.ChatException;
import com.live_commerce.chat.application.service.ChatService;
import com.live_commerce.chat.domain.model.Chat;
import com.live_commerce.chat.domain.model.MessageType;
import com.live_commerce.chat.infrastructure.client.BroadcastClient;
import com.live_commerce.chat.infrastructure.client.BroadcastStatus;
import com.live_commerce.chat.infrastructure.client.BroadcastStatusResponse;
import com.live_commerce.chat.infrastructure.redis.ChatRedisPublisher;
import com.live_commerce.chat.presentation.common.ApiResponse;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomWebSocketHandler extends TextWebSocketHandler {

    // 방송 ID (String)별로 접속한 유저들의 세션을 관리 (멀티 인스턴스 환경에서도 로컬 세션 관리 필요)
    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatService chatService;
    private final JwtUtil jwtUtil;
    private final BroadcastClient broadcastClient;
    private final ChatRedisPublisher chatRedisPublisher;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // HandshakeInterceptor에서 설정한 attributes에서 사용자 정보 추출
        String userId = (String) session.getAttributes().get("userId");
        String username = (String) session.getAttributes().get("username");
        String role = (String) session.getAttributes().get("role");

        if (userId == null || role == null) {
            log.warn("WebSocket 연결 실패: 인증 정보 없음 - sessionId: {}", session.getId());
            try {
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized: Missing authentication"));
            } catch (Exception e) {
                log.error("세션 종료 실패", e);
            }
            return;
        }

        // UUID로 변환하여 세션에 저장
        session.getAttributes().put("userIdUUID", UUID.fromString(userId));

        log.info("WebSocket 연결 성공 - sessionId: {}, userId: {}, username: {}, role: {}",
            session.getId(), userId, username, role);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 메시지 파싱
        ChatCreateRequest request = objectMapper.readValue(message.getPayload(), ChatCreateRequest.class);

        // 세션에서 인증 정보 추출
        UUID userId = (UUID) session.getAttributes().get("userIdUUID");
        String role = (String) session.getAttributes().get("role");

        if (userId == null || role == null) {
            log.warn("인증되지 않은 사용자의 메시지 차단 - sessionId: {}", session.getId());
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized"));
            return;
        }

        // 방송 상태 검증
        try {
            ApiResponse<BroadcastStatusResponse> response = broadcastClient.getBroadcast(request.liveBroadcastId());
            BroadcastStatusResponse statusResponse = response.getData();

            if (statusResponse == null || statusResponse.broadcastStatus() != BroadcastStatus.LIVE) {
                log.warn("방송 중이 아닌 상태에서 채팅 시도 - userId: {}, broadcastId: {}, status: {}",
                    userId, request.liveBroadcastId(), statusResponse != null ? statusResponse.broadcastStatus() : "NULL");

                session.sendMessage(new TextMessage(
                    objectMapper.writeValueAsString(Map.of(
                        "error", "방송 중일 때만 채팅이 가능합니다.",
                        "broadcastStatus", statusResponse != null ? statusResponse.broadcastStatus() : "UNKNOWN"
                    ))
                ));
                return;
            }
            log.debug("방송 상태 검증 완료 - broadcastId: {}, status: LIVE", request.liveBroadcastId());
        } catch (Exception e) {
            log.error("방송 상태 조회 실패 - broadcastId: {}", request.liveBroadcastId(), e);
            session.sendMessage(new TextMessage(
                objectMapper.writeValueAsString(Map.of("error", "방송 정보를 확인할 수 없습니다."))
            ));
            return;
        }

        // 메시지 타입에 따른 처리
        String broadcastId = request.liveBroadcastId().toString();
        roomSessions.putIfAbsent(broadcastId, ConcurrentHashMap.newKeySet());

        switch (request.messageType()) {
            case ENTER -> handleEnter(session, userId, request);
            case TALK -> handleTalk(session, userId, request);
            case LEAVE -> handleLeave(session, userId, request);
            default -> log.warn("알 수 없는 메시지 타입: {}", request.messageType());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket 연결 종료됨: {}", session.getId());

        roomSessions.forEach((broadcastId, sessions) -> {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                roomSessions.remove(broadcastId);
            }
        });
    }

    /**
     * Redis Pub/Sub Subscriber에서 호출하는 메서드
     * 해당 방송에 연결된 모든 WebSocket 세션에 메시지 브로드캐스트
     */
    public void broadcast(UUID broadcastId, UUID userId, String content) {
        String broadcastIdStr = broadcastId.toString();

        // 해당 broadcastId에 연결된 세션 목록 가져오기
        Set<WebSocketSession> sessions = roomSessions.get(broadcastIdStr);

        if (sessions == null || sessions.isEmpty()) {
            log.debug("브로드캐스트 대상 세션 없음 - broadcastId: {}", broadcastId);
            return;
        }

        // 메시지 객체 생성
        Map<String, Object> message = Map.of(
            "liveBroadcastId", broadcastId.toString(),
            "userId", userId.toString(),
            "chatting", content,
            "timestamp", System.currentTimeMillis()
        );

        String messageJson;
        try {
            messageJson = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("메시지 JSON 변환 실패", e);
            return;
        }

        // 각 세션에 메시지 전송
        sessions.removeIf(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(messageJson));
                    return false; // 유지
                }
            } catch (Exception e) {
                log.error("WebSocket 메시지 전송 실패 - sessionId: {}", session.getId(), e);
            }
            return true; // 제거 (연결 끊긴 세션)
        });

        log.debug("브로드캐스트 완료 - broadcastId: {}, 전송 세션 수: {}", broadcastId, sessions.size());
    }

    private void handleEnter(WebSocketSession session, UUID userId, ChatCreateRequest request) {
        String broadcastId = request.liveBroadcastId().toString();
        roomSessions.get(broadcastId).add(session);

        // Redis Pub/Sub을 통해 입장 메시지 발행 (멀티 인스턴스 대응)
        ChatCreateRequest enterRequest = new ChatCreateRequest(
            request.liveBroadcastId(),
            userId,
            "입장하셨습니다.",
            MessageType.ENTER
        );
        chatRedisPublisher.publishChat(enterRequest, userId);

        log.info("입장 처리 완료 - userId: {}, 방송: {}", userId, broadcastId);
    }

    private void handleTalk(WebSocketSession session, UUID userId, ChatCreateRequest request) {
        String broadcastId = request.liveBroadcastId().toString();
        roomSessions.get(broadcastId).add(session);

        // Redis Pub/Sub을 통해 채팅 메시지 발행 (멀티 인스턴스 대응)
        // Subscriber에서 DB 저장 및 브로드캐스트 처리
        chatRedisPublisher.publishChat(request, userId);

        log.info("TALK 메시지 발행 - userId: {}, 방송: {}, 내용: {}", userId, broadcastId, request.chatting());
    }

    private void handleLeave(WebSocketSession session, UUID userId, ChatCreateRequest request) {
        String broadcastId = request.liveBroadcastId().toString();

        // 세션에서 채팅방 퇴장 처리
        Set<WebSocketSession> sessions = roomSessions.getOrDefault(broadcastId, Set.of());
        sessions.remove(session);

        // Redis Pub/Sub을 통해 퇴장 메시지 발행 (멀티 인스턴스 대응)
        ChatCreateRequest leaveRequest = new ChatCreateRequest(
            request.liveBroadcastId(),
            userId,
            "퇴장하셨습니다.",
            MessageType.LEAVE
        );
        chatRedisPublisher.publishChat(leaveRequest, userId);

        log.info("퇴장 처리 완료 - userId: {}, 방송: {}", userId, broadcastId);
    }

}

