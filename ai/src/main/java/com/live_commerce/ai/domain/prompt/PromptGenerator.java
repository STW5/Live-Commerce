package com.live_commerce.ai.domain.prompt;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class PromptGenerator {

	public String generate(List<String> messages) {
		StringBuilder prompt = new StringBuilder();
		prompt.append("다음은 실시간 라이브 방송 중 고객들이 남긴 채팅 메시지 목록입니다.\n\n");

		for (String msg : messages) {
			prompt.append("- ").append(msg).append("\n");
		}

		prompt.append("\n이 채팅 내용을 기반으로 다음 내용을 간결하고 명확하게 작성해줘:\n");
		prompt.append("1. 전체적인 감정 흐름 요약 (예: 긍정적, 불안함, 궁금증 등)\n");
		prompt.append("2. 고객들이 가장 관심 있어 한 주요 니즈 3~5개\n");
		prompt.append("3. 방송 중 쇼호스트가 개선하거나 주의해야 할 점을 **직접적인 피드백** 형태로 작성 (예: 설명 부족, 질문 반응 속도, 정보 제공의 명확성 등)\n");
		prompt.append("4. 고객 질문에서 유의미한 키워드 5~10개 추출\n\n");
		prompt.append("출력 형식은 다음과 같이 항상 동일하게 작성:\n");
		prompt.append("---\n");
		prompt.append("📊 감정 요약: ...\n");
		prompt.append("🔍 주요 니즈: ...\n");
		prompt.append("🛠️ 쇼호스트 피드백: ...\n");
		prompt.append("#️⃣ 키워드: ...\n");
		prompt.append("---");

		return prompt.toString();
	}
}
