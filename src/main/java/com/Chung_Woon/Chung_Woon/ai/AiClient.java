package com.Chung_Woon.Chung_Woon.ai;

import com.Chung_Woon.Chung_Woon.ai.dto.BillOfLadingExtractionResponse;
import com.Chung_Woon.Chung_Woon.ai.dto.ParseInstructionRequest;
import com.Chung_Woon.Chung_Woon.ai.dto.ParseInstructionResponse;
import com.Chung_Woon.Chung_Woon.ai.dto.ReplanRequest;
import com.Chung_Woon.Chung_Woon.ai.dto.ReplanResponse;
import com.Chung_Woon.Chung_Woon.global.error.BusinessException;
import com.Chung_Woon.Chung_Woon.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * 파이썬 AI 서비스(:8000, 내부 전용)를 부른다. 외부에 노출하지 않는다 —
 * 이 클래스를 부르는 건 스프링 서비스 계층뿐이어야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

	private final RestClient aiRestClient;

	/** 헬스체크 전용. 대시보드가 AI 콜드스타트에 8초씩 매달리지 않게 타임아웃이 짧다. */
	private final RestClient aiHealthRestClient;

	/**
	 * {@code GET /health} 응답. 대시보드가 "AI 엔진 상태" 를 그릴 때 쓴다.
	 *
	 * @param up            응답이 왔는지
	 * @param geminiEnabled 키가 실제로 들어가 있는지. false 면 서버는 200 을 주지만 고정 더미를 돌려준다
	 */
	public record HealthSnapshot(boolean up, Boolean geminiEnabled) {

		static HealthSnapshot down() {
			return new HealthSnapshot(false, null);
		}
	}

	/**
	 * AI 서비스가 살아있는지. <b>절대 예외를 던지지 않는다</b> — 파이썬이 죽어도 대시보드는 떠야 한다
	 * (docs/API_CONTRACT.md "파이썬이 죽어 있을 때").
	 */
	public HealthSnapshot health() {
		try {
			AiHealthResponse body = aiHealthRestClient.get()
					.uri("/health")
					.retrieve()
					.body(AiHealthResponse.class);
			if (body == null) {
				return HealthSnapshot.down();
			}
			return new HealthSnapshot(true, body.geminiEnabled());
		} catch (RestClientException e) {
			log.debug("AI 헬스체크 실패: {}", e.getMessage());
			return HealthSnapshot.down();
		}
	}

	/** {@code /health} 의 응답 모양. 필요한 두 필드만 받는다. */
	private record AiHealthResponse(String status, Boolean geminiEnabled) {
	}

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

	/**
	 * 선하증권 이미지 → 문서에 적힌 값. 개별 차량 전개는 여기서 하지 않는다(호출부 책임).
	 *
	 * @throws BusinessException {@link ErrorCode#AI_UNAVAILABLE} 파이썬 호출 자체가 실패했을 때.
	 *                            docs/API_CONTRACT.md "파이썬이 죽어 있을 때" 절 — 저장 실패는
	 *                            이 요청만 실패로 끝내고 나머지 화면은 살려둔다.
	 */
	public BillOfLadingExtractionResponse extractBillOfLading(MultipartFile file) {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		try {
			builder.part("file", file.getBytes())
					.filename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "bill_of_lading")
					.contentType(file.getContentType() != null
							? MediaType.parseMediaType(file.getContentType())
							: MediaType.APPLICATION_OCTET_STREAM);
		} catch (IOException e) {
			throw new UncheckedIOException("업로드된 파일을 읽지 못했습니다", e);
		}

		try {
			return aiRestClient.post()
					.uri("/internal/extract/bl")
					.contentType(MediaType.MULTIPART_FORM_DATA)
					.body(builder.build())
					.retrieve()
					.body(BillOfLadingExtractionResponse.class);
		} catch (RestClientException e) {
			log.warn("AI 서비스 호출 실패 (/internal/extract/bl): {}", e.getMessage());
			throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "선하증권 추출 서비스를 호출하지 못했습니다.");
		}
	}

	/**
	 * 승인된 제약 + 야드 현황 + 차량 목록 → 재배치 결과. 결정론적 코드다(파이썬 쪽도 AI 를 안 씀) —
	 * 다만 호출 경로는 다른 것들과 같다.
	 *
	 * @throws BusinessException {@link ErrorCode#AI_UNAVAILABLE} 파이썬 호출 자체가 실패했을 때.
	 */
	public ReplanResponse replan(ReplanRequest request) {
		try {
			return aiRestClient.post()
					.uri("/internal/replan")
					.contentType(MediaType.APPLICATION_JSON)
					.body(request)
					.retrieve()
					.body(ReplanResponse.class);
		} catch (RestClientException e) {
			log.warn("AI 서비스 호출 실패 (/internal/replan): {}", e.getMessage());
			throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "재배치 서비스를 호출하지 못했습니다.");
		}
	}
}
