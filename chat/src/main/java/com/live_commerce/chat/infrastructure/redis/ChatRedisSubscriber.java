package com.live_commerce.chat.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.live_commerce.chat.application.dto.message.ChatRedisPayload;
import com.live_commerce.chat.application.service.ChatService;
import com.live_commerce.chat.infrastructure.security.CustomWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRedisSubscriber implements MessageListener {

	private final ChatService chatService;
	private final ObjectMapper objectMapper;
	private final CustomWebSocketHandler webSocketHandler;

	@Override
	public void onMessage(Message message, byte[] pattern) {
		try {
			String body = new String(message.getBody());
			ChatRedisPayload payload = objectMapper.readValue(body, ChatRedisPayload.class);
			log.info("[Redis Pub/Sub] 채팅 메시지 수신: broadcastId={}, userId={}",
				payload.request().liveBroadcastId(), payload.userId());

			// 1. DB에 저장
			chatService.createChat(payload.request(), payload.userId());

			// 2. 해당 방송에 연결된 모든 WebSocket 세션에 브로드캐스트
			webSocketHandler.broadcast(
				payload.request().liveBroadcastId(),
				payload.userId(),
				payload.request().chatting()
			);

			log.debug("[Redis Pub/Sub] 채팅 메시지 처리 완료: {}", payload.request().chatting());
		} catch (Exception e) {
			log.error("[Redis Pub/Sub] 메시지 처리 실패: {}", e.getMessage(), e);
		}
	}
}
