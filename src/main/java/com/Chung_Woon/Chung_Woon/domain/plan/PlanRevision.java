package com.Chung_Woon.Chung_Woon.domain.plan;

import com.Chung_Woon.Chung_Woon.domain.instruction.Instruction;
import com.Chung_Woon.Chung_Woon.global.common.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 배치 계획 한 판. 장표의 Revision Log 가 이 엔티티의 목록이다.
 *
 * <p>재배치는 항상 "이전 판(basedOnVersion)에서 무엇이 바뀌었나"로 남는다. 덮어쓰지 않는 이유는,
 * 심사와 현장 모두 "왜 이 차가 움직였나"를 되짚을 수 있어야 하기 때문이다.
 */
@Entity
@Table(name = "plan_revision")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanRevision extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 사람이 읽는 판 번호. 예: "B0"(baseline), "B2-r3" */
	@Column(nullable = false, unique = true, length = 40)
	private String planVersion;

	/** 어느 판을 기준으로 부분 재배치했는지. baseline 이면 null. */
	@Column(length = 40)
	private String basedOnVersion;

	/** 이 재배치를 촉발한 지시. 정기 배치라면 null. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "triggered_by_instruction_id")
	private Instruction triggeredBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private PlanStatus status = PlanStatus.DRAFT;

	@Embedded
	private PlanKpi kpi;

	@Column(length = 40)
	private String approvedBy;

	private LocalDateTime approvedAt;

	/** 코드가 대조해 넘긴 "확인이 필요한 지점" 목록(JSON). 사진↔계획 불일치 등. */
	@Column(columnDefinition = "text")
	private String consistencyIssuesJson;

	/** AI 가 생성한 담당자용 브리핑 문장. 숫자는 전부 아래 kpi 에서만 가져온다. */
	@Column(columnDefinition = "text")
	private String briefing;

	@OneToMany(mappedBy = "planRevision", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	private List<Placement> placements = new ArrayList<>();

	@OneToMany(mappedBy = "planRevision", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	private List<MoveTask> moveTasks = new ArrayList<>();

	public void submitForApproval() {
		this.status = PlanStatus.PENDING_APPROVAL;
	}

	public void approve(String approver) {
		this.status = PlanStatus.APPROVED;
		this.approvedBy = approver;
		this.approvedAt = LocalDateTime.now();
	}

	public void reject(String approver) {
		this.status = PlanStatus.REJECTED;
		this.approvedBy = approver;
		this.approvedAt = LocalDateTime.now();
	}

	public void attachBriefing(String briefing) {
		this.briefing = briefing;
	}
}
