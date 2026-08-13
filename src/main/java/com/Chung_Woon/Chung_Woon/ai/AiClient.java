package com.Chung_Woon.Chung_Woon.ai;

import com.Chung_Woon.Chung_Woon.ai.dto.ParseInstructionRequest;
import com.Chung_Woon.Chung_Woon.ai.dto.ParseInstructionResponse;
import com.Chung_Woon.Chung_Woon.global.error.BusinessException;
import com.Chung_Woon.Chung_Woon.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 파이썬 AI 서비스(:8000, 내부 전용)를 부른다. 외부에 노출하지 않는다 —
 * 이 클래스를 부르는 건 스프링 서비스 계층뿐이어야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

	private final RestClient aiRestClient;

	/**
	 * 자연어 지시 → 제약 JSON. thread_id 는 지금은 자리만 잡아둔 값이다(그래프 미구현).
	 *
	 * @throws BusinessException {@link ErrorCode#AI_UNAVAILABLE} 파이썬 호출 자체가 실패했을 때.
	 *                            docs/API_CONTRACT.md "파이썬이 죽어 있을 때" 절 — 저장 실패는
	 *                            이 요청만 실패로 끝내고 나머지 화면은 살려둔다.
	 */
	public ParseInstructionResponse parseInstruction(ParseInstructionRequest request) {
		try {
			return aiRestClient.post()
					.uri("/internal/parse")
					.contentType(MediaType.APPLICATION_JSON)
					.body(request)
					.retrieve()
					.body(ParseInstructionResponse.class);
		} catch (RestClientException e) {
			log.warn("AI 서비스 호출 실패 (/internal/parse): {}", e.getMessage());
			throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "지시 파싱 서비스를 호출하지 못했습니다.");
		}
	}
}
