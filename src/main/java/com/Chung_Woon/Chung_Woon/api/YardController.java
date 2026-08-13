package com.Chung_Woon.Chung_Woon.api;

import com.Chung_Woon.Chung_Woon.ai.dto.ReplanRequest;
import com.Chung_Woon.Chung_Woon.domain.plan.YardQueryService;
import com.Chung_Woon.Chung_Woon.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 야드 현황·차량 마스터 조회 — 재배치 계산의 입력을 눈으로 확인하는 용도. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class YardController {

	private final YardQueryService yardQueryService;

	@GetMapping("/yard/state")
	public ApiResponse<ReplanRequest.YardState> yardState() {
		return ApiResponse.ok(yardQueryService.currentYardState());
	}

	@GetMapping("/vehicles")
	public ApiResponse<List<ReplanRequest.VehiclePayload>> vehicles() {
		return ApiResponse.ok(yardQueryService.plannableVehicles());
	}
}
