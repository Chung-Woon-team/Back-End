package com.Chung_Woon.Chung_Woon.api;

import com.Chung_Woon.Chung_Woon.api.dto.PlanBriefingResponse;
import com.Chung_Woon.Chung_Woon.api.dto.PlanKpiResponse;
import com.Chung_Woon.Chung_Woon.domain.plan.PlanBriefingService;
import com.Chung_Woon.Chung_Woon.domain.plan.PlanKpiService;
import com.Chung_Woon.Chung_Woon.domain.plan.PlanReviewService;
import com.Chung_Woon.Chung_Woon.domain.plan.ReplanExecutionService;
import com.Chung_Woon.Chung_Woon.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 배치 계획(Revision Log) — 재배치 트리거, 조회, 승인/반려. */
@Tag(name = "배치 계획", description = "재배치 계산과 판(revision) 승인 이력")
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

	private final ReplanExecutionService replanExecutionService;
	private final PlanReviewService planReviewService;
	private final PlanKpiService planKpiService;
	private final PlanBriefingService planBriefingService;

	@Operation(summary = "재배치 계산",
			description = """
					**승인된 제약만** 가지고 새 배치 판을 계산한다. 계산은 파이썬의 결정론적 코드가
					하고 AI(LLM)는 쓰지 않는다.

					- 바디는 선택이다. 비우면 최신 승인 판을 기준으로 계산한다
					- 결과는 DRAFT 로 저장된다 — **승인 전에는 현장에 반영되지 않는다**
					- 응답의 moves[].path 는 좌표열이라 **저장되지 않는다.** 경로 애니메이션을
					  그리려면 이 응답을 받은 시점에 그려야 하고, 나중에 다시 보려면 재계산해야 한다
					""")
	@PostMapping
	public ApiResponse<ReplanExecutionService.ExecutionResult> create(
			@RequestBody(required = false) CreatePlanRequest request) {
		String basePlanVersion = request != null ? request.basePlanVersion() : null;
		String triggeredByInstructionId = request != null ? request.triggeredByInstructionId() : null;
		return ApiResponse.ok(replanExecutionService.executeReplan(basePlanVersion, triggeredByInstructionId));
	}

	@Operation(summary = "판 목록 (리비전 로그)",
			description = "최신순. 왜 이 차가 움직였는지 답할 수 있어야 해서 판을 덮어쓰지 않고 쌓는다.")
	@GetMapping
	public ApiResponse<List<PlanReviewService.PlanSummary>> list() {
		return ApiResponse.ok(planReviewService.list());
	}

	@Operation(summary = "판 상세",
			description = "배치 결과(placements) · 작업지시(moves) · KPI 를 함께 준다. "
					+ "전후 비교(지수·증감문구)는 `GET /{planVersion}/kpi` 를 쓴다.")
	@GetMapping("/{planVersion}")
	public ApiResponse<PlanReviewService.PlanDetail> get(@PathVariable String planVersion) {
		return ApiResponse.ok(planReviewService.get(planVersion));
	}

	@Operation(summary = "판 KPI 전후 비교",
			description = """
					한 판(revision)의 KPI 6개를 **이전 판과 대조해서** 지수·증감문구까지 완성해 준다.
					장표 6·9쪽 막대그래프가 `metrics[].index_before` / `index_after` 다.

					- `planVersion` 자리에 **`latest`** 를 넣으면 가장 최근 승인 판(없으면 가장 최근 판)을 쓴다
					- **비교할 이전 판이 없으면** `comparable: false` 이고 `before`·`index_*`·`delta_*` **키가 통째로 빠진다**
					  (값이 `null` 이 아니라 키가 없다 — 전역 `non_null` 설정)
					- 이 API 는 **파이썬을 호출하지 않는다.** AI 서비스가 죽어 있어도 200 이다
					- 서버가 계산하지 않는다: KPI 6개 수치는 재배치 계산 시점에 저장된 값을 그대로 읽는다
					""")
	@GetMapping("/{planVersion}/kpi")
	public ApiResponse<PlanKpiResponse> kpi(@PathVariable String planVersion) {
		return ApiResponse.ok(planKpiService.load(planVersion));
	}

	@Operation(summary = "브리핑 조회",
			description = """
					저장된 브리핑 문장과 확인 항목을 읽는다. **DB 만 읽으므로 파이썬이 죽어 있어도 200** 이다.

					- `planVersion` 자리에 **`latest`** 를 넣을 수 있다
					- 아직 생성 전이어도 **404 가 아니라 200** 이다 — `state` 가 `NOT_GENERATED` 이고
					  `briefing`·`source`·`generated_at` **키가 통째로 빠진다**. 이때 프론트는
					  "브리핑 생성" 버튼을 띄우고 `POST` 를 건다
					- `briefing` 은 `\\n` 로 줄바꿈된 통짜 문자열이다(`white-space: pre-line` 로 출력)
					- "확인이 필요한 지점"은 문장에 섞지 않고 `confirmations` 로 따로 준다
					""")
	@GetMapping("/{planVersion}/briefing")
	public ApiResponse<PlanBriefingResponse> briefing(@PathVariable String planVersion) {
		return ApiResponse.ok(planBriefingService.load(planVersion));
	}

	@Operation(summary = "브리핑 생성",
			description = """
					저장된 KPI 와 이동 목록을 파이썬 `/internal/brief` 에 넘겨 담당자용 브리핑 문장을
					만들고 **저장한 뒤** 돌려준다. 요청 바디는 없다.

					- **호출할 때마다 다시 만들어 덮어쓴다**(멱등하지 않음)
					- **숫자는 전부 저장된 KPI 에서만 나온다.** LLM 은 숫자를 만들지 않는다
					- Gemini 키가 없어도 200 이다 — 이때 `source` 가 `FALLBACK` 이다
					- **파이썬이 떠 있어야 한다.** 죽어 있으면 503 `AI001` (조회 API 인 `GET .../briefing` 은 파이썬 없이도 200)
					- KPI 6개 중 하나라도 없는 판은 400 `C001` — 브리핑을 만들 재료가 없다
					- 값이 없으면 키가 통째로 빠진다(전역 `non_null`)
					""")
	@PostMapping("/{planVersion}/briefing")
	public ApiResponse<PlanBriefingResponse> generateBriefing(@PathVariable String planVersion) {
		return ApiResponse.ok(planBriefingService.generate(planVersion));
	}

	@Operation(summary = "판 승인",
			description = "승인해야 현장에 반영된 것으로 본다. 대시보드의 평균 이동거리도 "
					+ "**승인된 판**만 반영한다.")
	@PatchMapping("/{planVersion}/approve")
	public ApiResponse<PlanReviewService.PlanSummary> approve(
			@PathVariable String planVersion, @RequestBody ReviewRequest request) {
		return ApiResponse.ok(planReviewService.approve(planVersion, request.reviewer()));
	}

	@Operation(summary = "판 반려",
			description = "반려된 판은 이력으로만 남고 배치에 반영되지 않는다.")
	@PatchMapping("/{planVersion}/reject")
	public ApiResponse<PlanReviewService.PlanSummary> reject(
			@PathVariable String planVersion, @RequestBody ReviewRequest request) {
		return ApiResponse.ok(planReviewService.reject(planVersion, request.reviewer()));
	}

	public record CreatePlanRequest(String basePlanVersion, String triggeredByInstructionId) {
	}

	public record ReviewRequest(String reviewer) {
	}
}
