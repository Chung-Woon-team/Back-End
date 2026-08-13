package com.Chung_Woon.Chung_Woon.domain.instruction;

import com.Chung_Woon.Chung_Woon.global.common.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 현장 관리자가 무전·메신저로 남긴 자연어 지시 원문. PK 는 "INS-001".
 *
 * <p>원문을 반드시 그대로 보관한다. 파싱이 틀렸을 때 무엇이 틀렸는지 대조할 근거가 되고,
 * 심사에서 "AI가 뭘 보고 이렇게 판단했나"를 보여주는 자료가 된다.
 */
@Entity
@Table(name = "instruction")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Instruction extends BaseTimeEntity {

	@Id
	@Column(name = "instruction_id", length = 30)
	private String instructionId;

	/** 지시 원문. 예: "오늘 14시부터 3번 블록은 도색작업으로 폐쇄해." */
	@Column(nullable = false, columnDefinition = "text")
	private String rawText;

	/** 지시를 남긴 사람 */
	@Column(length = 40)
	private String author;

	/**
	 * 파서가 해석하지 못한 표현 목록(JSON 배열 문자열).
	 * 비어 있지 않으면 되묻기(ask_human) 대상이다.
	 */
	@Column(columnDefinition = "text")
	private String unresolvedJson;

	/** 담당자 확인이 필요한 지시인지. 파서의 confidence 가 낮거나 unresolved 가 있으면 true. */
	@Column(nullable = false)
	@Builder.Default
	private boolean requiresConfirmation = false;

	/** 되묻기 문답 이력(JSON). 명확화 질문과 답변을 남겨 재현 가능하게 한다. */
	@Column(columnDefinition = "text")
	private String clarificationJson;

	/**
	 * 파이썬 LangGraph 의 승인 대기 스레드 ID. 그래프가 아직 없어서 지금은 자리만 잡아둔 값이
	 * 들어온다 — {@code /internal/resume} 이 구현되면 이 값으로 승인 결과를 되돌려 보낸다
	 * (docs/API_CONTRACT.md "thread_id — 승인 흐름의 핵심").
	 */
	@Column(length = 40)
	private String threadId;

	@OneToMany(mappedBy = "instruction", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	private List<PlanConstraint> constraints = new ArrayList<>();

	public void addConstraint(PlanConstraint constraint) {
		this.constraints.add(constraint);
		constraint.linkTo(this);
	}

	/** AI 파싱 결과를 받은 뒤 원문 지시에 되묻기 상태를 기록한다. */
	public void applyParseResult(String threadId, String unresolvedJson, boolean requiresConfirmation) {
		this.threadId = threadId;
		this.unresolvedJson = unresolvedJson;
		this.requiresConfirmation = requiresConfirmation;
	}
}
