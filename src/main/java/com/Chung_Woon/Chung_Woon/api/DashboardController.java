package com.Chung_Woon.Chung_Woon.api;

import com.Chung_Woon.Chung_Woon.api.dto.DashboardResponse;
import com.Chung_Woon.Chung_Woon.domain.dashboard.DashboardService;
import com.Chung_Woon.Chung_Woon.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관제 콘솔 대시보드. 화면 하나가 요청 하나로 채워진다.
 *
 * <p>프론트가 카드·블록·활동·지시를 각각 다른 API 로 긁어모으지 않게 한 데 모아서 준다.
 */
@Tag(name = "대시보드", description = "관제 콘솔 첫 화면")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

	private final DashboardService dashboardService;

	@Operation(
			summary = "대시보드 한 화면",
			description = """
					카드 4개 · 블록 4개 · 최근 활동 · 최근 지시 · AI 엔진 상태를 요청 하나로 준다.

					표시 문구(`status_label`, `note`)는 서버가 만든다 — 프론트가 depth·재취급 같은
					도메인 개념을 몰라도 화면을 그릴 수 있어야 한다.

					**값이 없으면 `null` 이 아니라 키가 통째로 빠진다**(전역 `non_null` 설정).
					승인된 판이 없으면 `avg_move_distance_meters` 자체가 없다. `=== null` 이 아니라
					`?.` / `in` 으로 다뤄야 한다.

					`ai_engine.status` 가 `DEGRADED` 면 AI 는 살아 있지만 Gemini 키가 없어
					**고정 더미 데이터**를 돌려주는 상태다.
					""")
	@GetMapping
	public ApiResponse<DashboardResponse> dashboard() {
		return ApiResponse.ok(dashboardService.load());
	}
}
