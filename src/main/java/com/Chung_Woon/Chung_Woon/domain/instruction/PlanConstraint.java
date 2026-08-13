package com.Chung_Woon.Chung_Woon.domain.instruction;

import com.Chung_Woon.Chung_Woon.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 자연어 지시에서 뽑아낸 제약조건 한 건. PK 는 "C-001".
 *
 * <p>이 시스템의 핵심 안전장치가 여기 걸린다 — 제약은 <b>승인(APPROVED)되기 전에는 배치에 반영되지 않는다.</b>
 * 파서가 만들어낸 직후 상태는 항상 {@link ConstraintStatus#PENDING_REVIEW} 이다.
 *
 * <p>클래스명이 {@code Constraint} 가 아닌 이유: {@code jakarta.validation.Constraint} 와 헷갈린다.
 */
@Entity
@Table(name = "plan_constraint")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanConstraint extends BaseTimeEntity {

	@Id
	@Column(name = "constraint_id", length = 30)
	private String constraintId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "instruction_id")
	private Instruction instruction;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private ConstraintType type;

	/** HARD 는 위반 시 배치 자체가 거부된다. SOFT 는 목적함수에 페널티로만 들어간다. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private ConstraintPriority priority;

	/** 적용 대상(JSON). 예: {"block_ids": ["B03"]} 또는 {"attribute": "brand", "values": ["B"]} */
	@Column(columnDefinition = "text")
	private String targetJson;

	/** 제약값(JSON). 예: {"preferred_zone": "WEST"} */
	@Column(columnDefinition = "text")
	private String valueJson;

	/** 적용 시작 시각. null 이면 즉시 적용. */
	private LocalDateTime windowStart;

	/** 적용 종료 시각. null 이면 해제 전까지 계속. */
	private LocalDateTime windowEnd;

	/** 파서 신뢰도 0.0~1.0. 임계값 미만이면 PENDING_REVIEW 로 붙잡아 둔다. */
	@Column(nullable = false)
	private double confidence;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private ConstraintStatus status = ConstraintStatus.PENDING_REVIEW;

	/** 승인·반려한 담당자 */
	@Column(length = 40)
	private String reviewedBy;

	private LocalDateTime reviewedAt;

	/** 반려 사유. 반려도 이력으로 남겨야 "왜 이 지시가 반영 안 됐나"에 답할 수 있다. */
	@Column(columnDefinition = "text")
	private String rejectionReason;

	void linkTo(Instruction instruction) {
		this.instruction = instruction;
	}

	public void approve(String reviewer) {
		this.status = ConstraintStatus.APPROVED;
		this.reviewedBy = reviewer;
		this.reviewedAt = LocalDateTime.now();
	}

	public void reject(String reviewer, String reason) {
		this.status = ConstraintStatus.REJECTED;
		this.reviewedBy = reviewer;
		this.reviewedAt = LocalDateTime.now();
		this.rejectionReason = reason;
	}

	/** 최적화 엔진에 넘겨도 되는 제약인지. 승인된 것만 통과시킨다. */
	public boolean isApplicable() {
		return this.status == ConstraintStatus.APPROVED;
	}
}
