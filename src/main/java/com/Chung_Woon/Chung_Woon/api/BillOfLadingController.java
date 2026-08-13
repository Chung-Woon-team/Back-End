package com.Chung_Woon.Chung_Woon.api;

import com.Chung_Woon.Chung_Woon.domain.billoflading.BillOfLadingExpansionService;
import com.Chung_Woon.Chung_Woon.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 선하증권 업로드 — 유일한 공개 창구. React 는 이 엔드포인트만 알면 되고, 내부적으로
 * 파이썬 AI 서비스(추출)와 결정론적 전개 로직(BillOfLadingExpansionService)을 순서대로 탄다.
 */
@Tag(name = "선하증권", description = "선하증권 이미지에서 차량 정보 추출")
@RestController
@RequestMapping("/api/bill-of-ladings")
@RequiredArgsConstructor
public class BillOfLadingController {

	private final BillOfLadingExpansionService expansionService;

	@Operation(summary = "선하증권 업로드 → 차량 전개",
			description = """
					선하증권 이미지를 올리면 AI 가 문서에 적힌 값을 읽고, **결정론적 코드**가
					차량 N행으로 펼쳐 저장한다. VIN 전개를 AI 에게 시키지 않는 이유는
					없는 VIN 을 지어내기 때문이다.

					- multipart/form-data 의 file 파트로 이미지를 보낸다 (최대 20MB)
					- 이미지 처리라 **10~30초** 걸릴 수 있다
					- 응답의 vehicle_count 는 문서의 UNITS 와 같아야 한다

					⚠️ **같은 선하증권을 두 번 올리면 500 이 난다** — VIN 이 unique 라 중복에서 걸린다.
					⚠️ AI 에 Gemini 키가 없으면 200 으로 **고정 더미 데이터**가 온다.
					GET /api/dashboard 의 ai_engine.status 가 DEGRADED 인지 확인할 것.
					""")
	@PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<BillOfLadingExpansionService.Result> extract(@RequestParam("file") MultipartFile file) {
		return ApiResponse.ok(expansionService.expand(file));
	}
}
