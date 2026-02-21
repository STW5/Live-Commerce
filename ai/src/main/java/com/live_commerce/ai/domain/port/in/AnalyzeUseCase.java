package com.live_commerce.ai.domain.port.in;

import com.live_commerce.ai.application.dto.command.AnalyzeCommand;
import com.live_commerce.ai.application.dto.result.AiResult;

public interface AnalyzeUseCase {
	AiResult analyze(AnalyzeCommand command);
}
