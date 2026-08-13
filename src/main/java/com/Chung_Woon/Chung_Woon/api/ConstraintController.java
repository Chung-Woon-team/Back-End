package com.Chung_Woon.Chung_Woon.api;

import com.Chung_Woon.Chung_Woon.domain.instruction.ConstraintReviewService;
import com.Chung_Woon.Chung_Woon.domain.instruction.ConstraintStatus;
import com.Chung_Woon.Chung_Woon.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "제약", description = "파싱된 제약의 조회와 승인·반려")
@RestController
@RequestMapping("/api/constraints")
@RequiredArgsConstructor
public class ConstraintController {

	private final ConstraintReviewService constraintReviewService;

	@Operation(summary = "제약 목록",
			description = """
					status 로 거른다. 값을 안 주면 전부 나온다.

					- PENDING_REVIEW — 승인 대기. 검토 화면이 보여줄 목록
					- APPROVED — 승인됨. 배치 계산에 들어가는 것
					- REJECTED — 반려됨. rejection_reason 이 채워져 있다

					target_json / value_json 은 **JSON 문자열**이라 화면에서 한 번 더 파싱해야 한다.
					""")
	@GetMapping
	public ApiResponse<List<ConstraintReviewService.ConstraintSummary>> list(
			@RequestParam(required = false) ConstraintStatus status) {
		return ApiResponse.ok(constraintReviewService.findByStatus(status));
	}

	@Operation(summary = "제약 승인",
			description = "승인해야 배치 계산에 반영된다. reviewed_by 와 reviewed_at 이 기록되고 "
					+ "되돌릴 수 없다. BLOCK_CLOSURE 제약이 승인되면 해당 블록이 실제로 닫힌다.")
	@PatchMapping("/{id}/approve")
	public ApiResponse<ConstraintReviewService.ConstraintSummary> approve(
			@PathVariable("id") String id, @RequestBody ApproveRequest request) {
		return ApiResponse.ok(constraintReviewService.approve(id, request.reviewer()));
	}

	@Operation(summary = "제약 반려",
			description = "반려 사유(reason)를 같이 남긴다. 반려된 제약은 배치 계산에서 빠지지만 "
					+ "기록은 남는다 — 왜 반영하지 않았는지 답할 수 있어야 한다.")
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
