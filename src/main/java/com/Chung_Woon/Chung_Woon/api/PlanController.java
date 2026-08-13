package com.Chung_Woon.Chung_Woon.api;

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
					+ "kpi_before 가 있으면 이전 판과 비교해 개선폭을 보여줄 수 있다.")
	@GetMapping("/{planVersion}")
	public ApiResponse<PlanReviewService.PlanDetail> get(@PathVariable String planVersion) {
		return ApiResponse.ok(planReviewService.get(planVersion));
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
