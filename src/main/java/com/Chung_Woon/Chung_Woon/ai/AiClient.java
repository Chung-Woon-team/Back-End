package com.Chung_Woon.Chung_Woon.ai;

import com.Chung_Woon.Chung_Woon.ai.dto.BillOfLadingExtractionResponse;
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
}
