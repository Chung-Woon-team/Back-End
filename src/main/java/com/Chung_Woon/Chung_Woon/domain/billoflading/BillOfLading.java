package com.Chung_Woon.Chung_Woon.domain.billoflading;

import com.Chung_Woon.Chung_Woon.domain.vehicle.Powertrain;
import com.Chung_Woon.Chung_Woon.domain.vehicle.UnloadingPriority;
import com.Chung_Woon.Chung_Woon.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 선하증권 1장. 차량 수십 대가 이 단위로 묶여 들어온다 (샘플 기준 60대 / 42대 / 18대).
 *
 * <p>PK 는 문서에 찍힌 B/L 번호 그대로 — "NXR-USN-NTD-26081101".
 *
 * <p>아래 필드 중 {@code unloadingPriority} 부터 {@code specialHandling} 까지는 문서의
 * "PCTC DISCHARGE DATA" 구획에서 온다. 그 구획은 문서에 <b>FOR HACKATHON EXTRACTION /
 * OPTIMISATION</b> 이라고 명시돼 있는, 배치 최적화에 쓰라고 넣어준 값들이다.
 */
@Entity
@Table(name = "bill_of_lading", indexes = {
		@Index(name = "idx_bl_voyage", columnList = "voyageNumber"),
		@Index(name = "idx_bl_lot", columnList = "lotCode")
})
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillOfLading extends BaseTimeEntity {

	/** B/L 또는 Waybill 번호. 예: "NXR-USN-NTD-26081101" */
	@Id
	@Column(name = "bl_number", length = 40)
	private String blNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private DocumentType documentType;

	// --- 코드 번호들 ---

	/** 예약 번호. 예: "NXR-SP2608E-001" */
	@Column(length = 40)
	private String bookingNumber;

	/** 로트 코드. 예: "EV-A-0811" — 같은 로트끼리 모아 두면 나중에 통째로 꺼내기 쉽다. */
	@Column(length = 40)
	private String lotCode;

	/** 연계 문서. 예: "ROUTE KRUSN-USNTD-260811" */
	@Column(length = 60)
	private String linkedRouteCode;

	// --- 선박 / 항로 ---

	/** 예: "MV SYNTH PACIFIC" */
	@Column(length = 80)
	private String vesselName;

	/** 항차. 예: "SP2608E" */
	@Column(length = 40)
	private String voyageNumber;

	/** 선적항 UN/LOCODE. 예: "KRUSN" */
	@Column(length = 10)
	private String portOfLoading;

	/** 양하항 UN/LOCODE. 예: "USNTD" */
	@Column(length = 10)
	private String portOfDischarge;

	// --- 당사자 ---

	@Column(length = 120)
	private String shipperName;

	@Column(length = 120)
	private String consigneeName;

	@Column(length = 120)
	private String notifyParty;

	// --- 화물 요약 ---

	private LocalDate issueDate;

	/** 총 대수. 개별 Vehicle 행 수와 일치해야 한다(검증 대상). */
	private Integer unitCount;

	private Integer grossWeightKg;

	private Double measurementCbm;

	// --- PCTC DISCHARGE DATA (최적화 입력) ---

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private Powertrain powertrain;

	/** 자력주행 가능 대수 */
	private Integer driveableCount;

	/** 견인 필요 대수. 0 이 아니면 하선 작업이 달라진다. */
	private Integer towCount;

	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private UnloadingPriority unloadingPriority;

	/** 선하증권이 지정한 목표 구역. 예: "EV-A / ROWS 01-06", "QC-HOLD / BAYS 17 & 31" */
	@Column(length = 60)
	private String targetYardZone;

	/** 하선 순번 구간 시작. 예: SEQ 041-100 의 41 */
	private Integer dischargeSeqFrom;

	/** 하선 순번 구간 끝. 예: SEQ 041-100 의 100 */
	private Integer dischargeSeqTo;

	/** 취급 주의사항. 예: "NO IDLING · EV FIRE LANE 6 M", "MAX HEIGHT 3.80 M · ESCORT" */
	@Column(length = 200)
	private String specialHandling;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private FreightTerms freightTerms;
}
