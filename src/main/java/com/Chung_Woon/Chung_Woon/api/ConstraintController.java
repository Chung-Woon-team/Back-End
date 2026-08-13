package com.Chung_Woon.Chung_Woon.api;

import com.Chung_Woon.Chung_Woon.domain.instruction.ConstraintReviewService;
import com.Chung_Woon.Chung_Woon.domain.instruction.ConstraintStatus;
import com.Chung_Woon.Chung_Woon.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 제약 조회·승인·반려. 승인 전에는 {@link com.Chung_Woon.Chung_Woon.domain.instruction.PlanConstraint} 가 최적화에 반영되지 않는다. */
@RestController
@RequestMapping("/api/constraints")
@RequiredArgsConstructor
public class ConstraintController {

	private final ConstraintReviewService constraintReviewService;

	@GetMapping
	public ApiResponse<List<ConstraintReviewService.ConstraintSummary>> list(
			@RequestParam(required = false) ConstraintStatus status) {
		return ApiResponse.ok(constraintReviewService.findByStatus(status));
	}

	@PatchMapping("/{id}/approve")
	public ApiResponse<ConstraintReviewService.ConstraintSummary> approve(
			@PathVariable("id") String id, @RequestBody ApproveRequest request) {
		return ApiResponse.ok(constraintReviewService.approve(id, request.reviewer()));
	}

	@PatchMapping("/{id}/reject")
	public ApiResponse<ConstraintReviewService.ConstraintSummary> reject(
			@PathVariable("id") String id, @RequestBody RejectRequest request) {
		return ApiResponse.ok(constraintReviewService.reject(id, request.reviewer(), request.reason()));
	}

	public record ApproveRequest(String reviewer) {
	}

	public record RejectRequest(String reviewer, String reason) {
	}
}
