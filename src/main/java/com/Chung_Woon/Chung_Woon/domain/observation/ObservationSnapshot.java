package com.Chung_Woon.Chung_Woon.domain.observation;

import com.Chung_Woon.Chung_Woon.domain.yard.Block;
import com.Chung_Woon.Chung_Woon.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
 * 카메라·드론 사진 1장을 인식한 결과 = "그 시점 그 블록의 점유 상태".
 *
 * <p>계획(Placement)과 현실(이 스냅샷)이 다를 수 있고, 그 차이를 잡아내는 게
 * check_consistency 노드다. 그래서 관측은 계획과 별도 테이블로 남긴다.
 * {@code capturedAt} 은 업로드 시각이 아니라 <b>촬영 시각</b>이다 — 오래된 사진으로
 * 배치를 뒤집는 사고를 막으려면 이 구분이 필요하다.
 */
@Entity
@Table(name = "observation_snapshot")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ObservationSnapshot extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * 사진 한 장(야드 전체)이 블록 4개 행으로 갈라져 저장되므로, 같은 업로드에서 나온 행을
	 * 다시 묶어 부르기 위한 키. 확정(confirm) 단계가 이 값으로 그 배치를 다시 조회한다.
	 */
	@Column(nullable = false, length = 36)
	private String batchId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "block_id", nullable = false)
	private Block block;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ObservationSource source;

	/** 촬영 시각 (업로드 시각 아님) */
	@Column(nullable = false)
	private LocalDateTime capturedAt;

	/** 격자 점유 상태(JSON). [{"row":0,"col":0,"occupied":true}, ...] */
	@Column(nullable = false, columnDefinition = "text")
	private String gridJson;

	/** 인식 신뢰도 0.0~1.0 */
	@Column(nullable = false)
	private double confidence;

	/** 신뢰도가 낮아 담당자 확인이 필요한 관측인지 */
	@Column(nullable = false)
	@Builder.Default
	private boolean requiresConfirmation = false;
}
