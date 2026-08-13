package com.Chung_Woon.Chung_Woon.ai.dto;

import com.Chung_Woon.Chung_Woon.domain.billoflading.DocumentType;
import com.Chung_Woon.Chung_Woon.domain.vehicle.Powertrain;
import com.Chung_Woon.Chung_Woon.domain.vehicle.UnloadingPriority;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDate;
import java.util.List;

/**
 * 파이썬 {@code POST /internal/extract/bl} 의 응답 = {@code autoyard.schemas.BillOfLadingExtraction} 을
 * 그대로 옮긴 것. 필드가 어긋나면 역직렬화가 조용히 null 로 떨어지니, 저쪽 스키마가 바뀌면 여기도 같이
 * 고쳐야 한다(docs/DOMAIN.md 4-2절).
 *
 * <p>파이썬은 snake_case 로 응답한다(docs/API_CONTRACT.md 규칙 1) — 전역 Jackson 설정은
 * LOWER_CAMEL_CASE(프론트용) 라서, 이 DTO 에서만 개별적으로 SnakeCaseStrategy 를 건다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BillOfLadingExtractionResponse(
		String blNumber,
		DocumentType documentType,
		String bookingNumber,
		String lotCode,
		String linkedRouteCode,
		String vesselName,
		String voyageNumber,
		String portOfLoading,
		String portOfDischarge,
		LocalDate issueDate,
		String shipperName,
		String consigneeName,
		String notifyParty,
		List<CargoLine> cargoLines,
		Integer unitCount,
		Integer grossWeightKg,
		Double measurementCbm,
		String vinRangeFrom,
		String vinRangeTo,
		Powertrain powertrain,
		Integer driveableCount,
		Integer towCount,
		List<Integer> towUnitNumbers,
		UnloadingPriority unloadingPriority,
		String targetYardZone,
		Integer dischargeSeqFrom,
		Integer dischargeSeqTo,
		String specialHandling,
		Double confidence
) {

	/** 한 문서 안에 서로 다른 품목이 여러 줄인 경우(예: 버스 10대 + 트럭 8대, 높이가 다름). */
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record CargoLine(
			String description,
			Integer unitCount,
			String brand,
			String model,
			Double heightMeters,
			Boolean driveable
	) {
	}
}
