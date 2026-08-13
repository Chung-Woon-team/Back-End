package com.Chung_Woon.Chung_Woon.domain.plan;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장표 6·9쪽의 전후 비교 지표. 브리핑에 등장하는 모든 숫자는 여기서만 나온다
 * (AI활용방안 5절: "AI가 수치를 생성하지 않는다").
 */
@Embeddable
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanKpi {

	/** 평균 이동거리(m) */
	private Double avgMoveDistance;

	/** 재취급 Proxy. Lane-Depth 기반 추정치이며 실측이 아니다(장표 9쪽 한계 명시). */
	private Double rehandleProxy;

	/** Hard 제약 위반 건수. 목표는 0. */
	private Integer hardViolations;

	/** 이 판에서 실제로 움직인 차량 수. 부분 재배치의 핵심 지표. */
	private Integer changedVehicles;

	/** 계획 유지율(%). 이전 판 대비 자리를 지킨 차량 비율. */
	private Double planRetentionRate;

	/** 최적화 계산 소요 시간(ms). 장표 목표는 500대 기준 2초 이내. */
	private Long calcMillis;
}
