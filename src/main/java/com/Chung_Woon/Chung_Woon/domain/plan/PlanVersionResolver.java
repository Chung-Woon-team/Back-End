package com.Chung_Woon.Chung_Woon.domain.plan;

import com.Chung_Woon.Chung_Woon.global.error.BusinessException;
import com.Chung_Woon.Chung_Woon.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 경로 변수 {@code {planVersion}} 을 실제 판으로 바꾼다. 리터럴 {@code "latest"} 를 별칭으로 받는다 —
 * 프론트에 판 선택 UI 가 없어서 "지금 현장에 나가 있는 판" 을 가리킬 방법이 필요하다.
 *
 * <p>실제 판 번호는 {@code B0-r{hex6}} 형식이라 {@code latest} 와 충돌하지 않는다.
 *
 * <p>해석 우선순위는 <b>승인된 최신 판 → 그것도 없으면 가장 최근에 만들어진 판</b>이다. 승인 전 판을
 * 2순위로 두는 이유: 데모에서 아직 아무것도 승인하지 않았을 때 화면이 404 로 비어버리는 것보다,
 * 방금 계산한 DRAFT 라도 보여주는 편이 낫다({@code status} 로 구분할 수 있다).
 */
@Component
@RequiredArgsConstructor
public class PlanVersionResolver {

	/** 경로 변수 자리에 이 문자열이 오면 별칭으로 해석한다. */
	public static final String LATEST = "latest";

	private final PlanRevisionRepository planRevisionRepository;

	public PlanRevision resolve(String rawPlanVersion) {
		if (!LATEST.equals(rawPlanVersion)) {
			return planRevisionRepository.findByPlanVersion(rawPlanVersion)
					.orElseThrow(() -> new BusinessException(
							ErrorCode.NOT_FOUND, "계획을 찾을 수 없습니다: " + rawPlanVersion));
		}

		Optional<PlanRevision> approved =
				planRevisionRepository.findFirstByStatusOrderByApprovedAtDesc(PlanStatus.APPROVED);
		if (approved.isPresent()) {
			return approved.get();
		}

		List<PlanRevision> all = planRevisionRepository.findAllByOrderByIdDesc();
		if (all.isEmpty()) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "계획이 아직 하나도 없습니다: latest");
		}
		return all.get(0);
	}
}
