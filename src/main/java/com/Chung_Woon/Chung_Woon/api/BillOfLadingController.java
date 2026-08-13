package com.Chung_Woon.Chung_Woon.api;

import com.Chung_Woon.Chung_Woon.domain.billoflading.BillOfLadingExpansionService;
import com.Chung_Woon.Chung_Woon.global.common.ApiResponse;
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
@RestController
@RequestMapping("/api/bill-of-ladings")
@RequiredArgsConstructor
public class BillOfLadingController {

	private final BillOfLadingExpansionService expansionService;

	@PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<BillOfLadingExpansionService.Result> extract(@RequestParam("file") MultipartFile file) {
		return ApiResponse.ok(expansionService.expand(file));
	}
}
