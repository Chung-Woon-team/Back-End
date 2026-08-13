package com.Chung_Woon.Chung_Woon.api;

import com.Chung_Woon.Chung_Woon.ai.dto.ReplanRequest;
import com.Chung_Woon.Chung_Woon.domain.plan.YardQueryService;
import com.Chung_Woon.Chung_Woon.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 야드 현황·차량 마스터 조회 — 재배치 계산의 입력을 눈으로 확인하는 용도. */
@Tag(name = "야드", description = "야드 격자와 점유 상태")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class YardController {

	private final YardQueryService yardQueryService;

	@Operation(summary = "야드 상태 (알고리즘 입력용)",
			description = "재배치 계산에 그대로 넘기는 형태다. **화면을 그릴 목적이라면 "
					+ "GET /api/yard/occupancy 를 쓰는 편이 낫다** — 그쪽이 빈 칸까지 340개를 다 준다.")
	@GetMapping("/yard/state")
	public ApiResponse<ReplanRequest.YardState> yardState() {
		return ApiResponse.ok(yardQueryService.currentYardState());
	}

	@Operation(summary = "차량 목록",
			description = "배치 계산 대상 차량. 이미 출고된 차(DEPARTED)는 빠진다.")
	@GetMapping("/vehicles")
	public ApiResponse<List<ReplanRequest.VehiclePayload>> vehicles() {
		return ApiResponse.ok(yardQueryService.plannableVehicles());
	}
}
