package com.Chung_Woon.Chung_Woon.api;

import com.Chung_Woon.Chung_Woon.api.dto.YardOccupancyResponse;
import com.Chung_Woon.Chung_Woon.domain.yard.YardOccupancyService;
import com.Chung_Woon.Chung_Woon.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 야드 점유 스냅샷. 배치·경로 알고리즘의 입력이다.
 *
 * <p>시드 API 와 달리 <b>읽기 전용</b>이라 플래그 없이 항상 열려 있다.
 */
@RestController
@RequestMapping("/api/yard/occupancy")
@RequiredArgsConstructor
public class YardOccupancyController {

	private final YardOccupancyService yardOccupancyService;

	@GetMapping
	public ApiResponse<YardOccupancyResponse> occupancy() {
		return ApiResponse.ok(yardOccupancyService.snapshot());
	}
}
