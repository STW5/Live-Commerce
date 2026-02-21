package com.live_commerce.ai.application.dto.command;

import java.util.List;
import java.util.UUID;

public record AnalyzeCommand(
	UUID liveBroadcastId,
	List<String> messages,
	String secret
) {}
