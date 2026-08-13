package com.Chung_Woon.Chung_Woon.api;

import com.Chung_Woon.Chung_Woon.api.dto.DashboardResponse;
import com.Chung_Woon.Chung_Woon.domain.dashboard.DashboardService;
import com.Chung_Woon.Chung_Woon.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관제 콘솔 대시보드. 화면 하나가 요청 하나로 채워진다.
 *
 * <p>프론트가 카드·블록·활동·지시를 각각 다른 API 로 긁어모으지 않게 한 데 모아서 준다.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

	private final DashboardService dashboardService;

	@GetMapping
	public ApiResponse<DashboardResponse> dashboard() {
		return ApiResponse.ok(dashboardService.load());
	}
}
