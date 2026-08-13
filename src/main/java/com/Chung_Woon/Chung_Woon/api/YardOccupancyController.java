package com.Chung_Woon.Chung_Woon.api;

import com.Chung_Woon.Chung_Woon.api.dto.YardOccupancyResponse;
import com.Chung_Woon.Chung_Woon.domain.yard.YardOccupancyService;
import com.Chung_Woon.Chung_Woon.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 야드 점유 스냅샷. 배치·경로 알고리즘의 입력이다.
 *
 * <p>시드 API 와 달리 <b>읽기 전용</b>이라 플래그 없이 항상 열려 있다.
 */
@Tag(name = "야드", description = "야드 격자와 점유 상태")
@RestController
@RequestMapping("/api/yard/occupancy")
@RequiredArgsConstructor
public class YardOccupancyController {

	private final YardOccupancyService yardOccupancyService;

	@Operation(
			summary = "야드 전체 상태 조회",
			description = """
					격자 · 블록 4개 · 주차칸 340개를 전부 준다. **빈 칸도 포함**하므로 이 응답 하나로
					야드 화면을 통째로 그릴 수 있고, 배치·경로 알고리즘의 입력으로도 그대로 쓴다.

					격자 구조(어느 칸이 주차칸이고 레인·depth 가 얼마인지)는 **코드가 정본**이라
					DB 적재 여부와 무관하게 항상 340칸이 내려간다. DB 에서 오는 건 상태뿐이다 —
					누가 서 있고 어느 블록이 닫혔는지.

					`grid_seeded` 가 `false` 면 DB 에 격자가 아직 안 깔린 것이고, 그때 `slots` 의
					`status` 는 실제 상태가 아니라 전부 `EMPTY` 다. 서버를 재기동하면 채워진다.

					좌표는 알고리즘이 쓰는 (x, y) 그대로다 — **x = col, y = row**.
					닫힌 블록에는 새로 배치하지 않지만 **길로 지나가는 것은 허용**해야 한다.
					""")
	@GetMapping
	public ApiResponse<YardOccupancyResponse> occupancy() {
		return ApiResponse.ok(yardOccupancyService.snapshot());
	}
}
