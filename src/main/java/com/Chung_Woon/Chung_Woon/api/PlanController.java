package com.Chung_Woon.Chung_Woon.api;

import com.Chung_Woon.Chung_Woon.domain.plan.PlanReviewService;
import com.Chung_Woon.Chung_Woon.domain.plan.ReplanExecutionService;
import com.Chung_Woon.Chung_Woon.global.common.ApiResponse;
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
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

	private final ReplanExecutionService replanExecutionService;
	private final PlanReviewService planReviewService;

	@PostMapping
	public ApiResponse<ReplanExecutionService.ExecutionResult> create(
			@RequestBody(required = false) CreatePlanRequest request) {
		String basePlanVersion = request != null ? request.basePlanVersion() : null;
		String triggeredByInstructionId = request != null ? request.triggeredByInstructionId() : null;
		return ApiResponse.ok(replanExecutionService.executeReplan(basePlanVersion, triggeredByInstructionId));
	}

	@GetMapping
	public ApiResponse<List<PlanReviewService.PlanSummary>> list() {
		return ApiResponse.ok(planReviewService.list());
	}

	@GetMapping("/{planVersion}")
	public ApiResponse<PlanReviewService.PlanDetail> get(@PathVariable String planVersion) {
		return ApiResponse.ok(planReviewService.get(planVersion));
	}

	@PatchMapping("/{planVersion}/approve")
	public ApiResponse<PlanReviewService.PlanSummary> approve(
			@PathVariable String planVersion, @RequestBody ReviewRequest request) {
		return ApiResponse.ok(planReviewService.approve(planVersion, request.reviewer()));
	}

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
