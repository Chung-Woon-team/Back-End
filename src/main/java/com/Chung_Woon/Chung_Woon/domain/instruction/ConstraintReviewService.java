package com.Chung_Woon.Chung_Woon.domain.instruction;

import com.Chung_Woon.Chung_Woon.global.error.BusinessException;
import com.Chung_Woon.Chung_Woon.global.error.ErrorCode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 제약 승인/반려. 이 시스템의 핵심 안전장치 — {@link PlanConstraint#isApplicable()} 가
 * {@code APPROVED} 만 통과시키므로, 여기를 거치지 않은 제약은 최적화에 절대 반영되지 않는다.
 *
 * <p>엔티티를 컨트롤러로 그대로 내보내지 않고 여기서 DTO 로 바꿔 반환한다 —
 * {@code spring.jpa.open-in-view: false} 라 세션이 서비스 메서드 종료와 함께 닫히는데,
 * {@link PlanConstraint#getInstruction()} 이 LAZY 라 밖에서 건드리면 예외가 난다.
 */
@Service
@RequiredArgsConstructor
public class ConstraintReviewService {

	private final PlanConstraintRepository planConstraintRepository;

	@Transactional(readOnly = true)
	public List<ConstraintSummary> findByStatus(ConstraintStatus status) {
		List<PlanConstraint> constraints = status != null
				? planConstraintRepository.findByStatus(status)
				: planConstraintRepository.findAll();
		return constraints.stream().map(ConstraintSummary::from).toList();
	}

	@Transactional
	public ConstraintSummary approve(String constraintId, String reviewer) {
		PlanConstraint constraint = getOrThrow(constraintId);
		constraint.approve(reviewer);
		return ConstraintSummary.from(constraint);
	}

	@Transactional
	public ConstraintSummary reject(String constraintId, String reviewer, String reason) {
		PlanConstraint constraint = getOrThrow(constraintId);
		constraint.reject(reviewer, reason);
		return ConstraintSummary.from(constraint);
	}

	private PlanConstraint getOrThrow(String constraintId) {
		return planConstraintRepository.findById(constraintId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "제약을 찾을 수 없습니다: " + constraintId));
	}

	/**
	 * 공개 API 응답이라 snake_case 로 나가야 한다(API_CONTRACT.md 규칙 1, FRONTEND_CONTRACT.md 규칙 2).
	 * 전역 Jackson 설정이 LOWER_CAMEL_CASE 라 이 애노테이션이 없으면 프론트가 읽는 필드가 전부 undefined 다.
	 */
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record ConstraintSummary(
			String constraintId,
			String instructionId,
			ConstraintType type,
			ConstraintPriority priority,
			String targetJson,
			String valueJson,
			LocalDateTime windowStart,
			LocalDateTime windowEnd,
			double confidence,
			ConstraintStatus status,
			String reviewedBy,
			LocalDateTime reviewedAt,
			String rejectionReason
	) {
		static ConstraintSummary from(PlanConstraint c) {
			return new ConstraintSummary(
					c.getConstraintId(),
					c.getInstruction() != null ? c.getInstruction().getInstructionId() : null,
					c.getType(),
					c.getPriority(),
					c.getTargetJson(),
					c.getValueJson(),
					c.getWindowStart(),
					c.getWindowEnd(),
					c.getConfidence(),
					c.getStatus(),
					c.getReviewedBy(),
					c.getReviewedAt(),
					c.getRejectionReason()
			);
		}
	}
}
