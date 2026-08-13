package com.Chung_Woon.Chung_Woon.domain.billoflading;

import com.Chung_Woon.Chung_Woon.ai.AiClient;
import com.Chung_Woon.Chung_Woon.ai.dto.BillOfLadingExtractionResponse;
import com.Chung_Woon.Chung_Woon.domain.vehicle.Vehicle;
import com.Chung_Woon.Chung_Woon.domain.vehicle.VehicleRepository;
import com.Chung_Woon.Chung_Woon.global.error.BusinessException;
import com.Chung_Woon.Chung_Woon.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 선하증권 이미지 → BillOfLading 1행 + Vehicle N행.
 *
 * <p>AI 는 문서에 적힌 집계값({@link BillOfLadingExtractionResponse})까지만 만든다. VIN range 를
 * 개별 VIN 으로 펼치고 SEQ 구간을 차량마다 배분하는 건 여기, 결정론적 코드가 한다 — AI 에게 시키면
 * 없는 VIN 을 지어낸다(docs/DOMAIN.md 4-1절).
 */
@Service
@RequiredArgsConstructor
public class BillOfLadingExpansionService {

	private static final Pattern TRAILING_DIGITS = Pattern.compile("^(.*?)(\\d+)$");

	private final AiClient aiClient;
	private final BillOfLadingRepository billOfLadingRepository;
	private final VehicleRepository vehicleRepository;

	@Transactional
	public Result expand(MultipartFile blImage) {
		BillOfLadingExtractionResponse extraction = aiClient.extractBillOfLading(blImage);

		if (extraction.unitCount() == null || extraction.unitCount() <= 0) {
			throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID, "unit_count 가 비어있거나 0 이하입니다.");
		}

		BillOfLading billOfLading = toBillOfLading(extraction);
		billOfLadingRepository.save(billOfLading);

		List<Vehicle> vehicles = expandVehicles(extraction, billOfLading);
		vehicleRepository.saveAll(vehicles);

		return new Result(billOfLading.getBlNumber(), vehicles.size(),
				vehicles.stream().map(Vehicle::getVehicleId).toList());
	}

	private BillOfLading toBillOfLading(BillOfLadingExtractionResponse e) {
		return BillOfLading.builder()
				.blNumber(e.blNumber())
				.documentType(e.documentType())
				.bookingNumber(e.bookingNumber())
				.lotCode(e.lotCode())
				.linkedRouteCode(e.linkedRouteCode())
				.vesselName(e.vesselName())
				.voyageNumber(e.voyageNumber())
				.portOfLoading(e.portOfLoading())
				.portOfDischarge(e.portOfDischarge())
				.issueDate(e.issueDate())
				.shipperName(e.shipperName())
				.consigneeName(e.consigneeName())
				.notifyParty(e.notifyParty())
				.unitCount(e.unitCount())
				.grossWeightKg(e.grossWeightKg())
				.measurementCbm(e.measurementCbm())
				.powertrain(e.powertrain())
				.driveableCount(e.driveableCount())
				.towCount(e.towCount())
				.unloadingPriority(e.unloadingPriority())
				.targetYardZone(e.targetYardZone())
				.dischargeSeqFrom(e.dischargeSeqFrom())
				.dischargeSeqTo(e.dischargeSeqTo())
				.specialHandling(e.specialHandling())
				.build();
	}

	private List<Vehicle> expandVehicles(BillOfLadingExtractionResponse e, BillOfLading billOfLading) {
		int count = e.unitCount();
		List<String> vins = expandVins(e.vinRangeFrom(), count);
		List<Integer> sequences = expandSequence(e.dischargeSeqFrom(), count);
		Set<Integer> towUnitNumbers = e.towUnitNumbers() == null ? Set.of() : new HashSet<>(e.towUnitNumbers());
		List<BillOfLadingExtractionResponse.CargoLine> flatCargoLines = flattenCargoLines(e.cargoLines(), count);

		// 전역 유일해야 하는 4자리 ID(V-0001..V-9999) 라 기존 대수 다음부터 이어 붙인다.
		long startingOffset = vehicleRepository.count();

		List<Vehicle> vehicles = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			int unitNumber = i + 1; // 문서 안에서의 1-based 순번 - tow_unit_numbers 가 이 기준이다
			BillOfLadingExtractionResponse.CargoLine line = flatCargoLines.get(i);
			boolean driveable = resolveDriveable(line, towUnitNumbers, unitNumber);

			vehicles.add(Vehicle.builder()
					.vehicleId("V-%04d".formatted(startingOffset + i + 1))
					.vin(vins.get(i))
					.billOfLading(billOfLading)
					.brand(line != null ? line.brand() : null)
					.model(line != null ? line.model() : null)
					.dischargeSequence(sequences.get(i))
					.unloadingPriority(e.unloadingPriority())
					.powertrain(e.powertrain())
					.driveable(driveable)
					.heightMeters(line != null ? line.heightMeters() : null)
					.build());
		}
		return vehicles;
	}

	/**
	 * cargo_line 에 driveable 이 명시돼 있으면 그걸 쓰고, 없으면 tow_unit_numbers 기준으로 판정한다.
	 * package-private: 순수 로직이라 스프링 컨텍스트 없이 직접 테스트한다.
	 */
	boolean resolveDriveable(BillOfLadingExtractionResponse.CargoLine line, Set<Integer> towUnitNumbers,
			int unitNumber) {
		if (line != null && line.driveable() != null) {
			return line.driveable();
		}
		return !towUnitNumbers.contains(unitNumber);
	}

	/**
	 * vinRangeFrom 의 자릿수를 기준으로 순서대로 증가시킨다. vinRangeTo 는 참고하지 않는다 —
	 * 실측 사례에서 from/to 자릿수가 어긋난 적이 있다(끝자리 0 하나 누락, OCR 추정).
	 */
	List<String> expandVins(String vinRangeFrom, int count) {
		if (vinRangeFrom == null) {
			return nullList(count);
		}
		Matcher m = TRAILING_DIGITS.matcher(vinRangeFrom);
		if (!m.matches()) {
			return nullList(count);
		}
		String prefix = m.group(1);
		String digits = m.group(2);
		long start = Long.parseLong(digits);
		int width = digits.length();

		List<String> vins = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			vins.add(prefix + String.format("%0" + width + "d", start + i));
		}
		return vins;
	}

	List<Integer> expandSequence(Integer from, int count) {
		List<Integer> sequences = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			sequences.add(from != null ? from + i : null);
		}
		return sequences;
	}

	/**
	 * cargo_lines 를 순서대로 펼쳐 차량 인덱스에 대응시킨다(예: 버스 10대 다음 트럭 8대).
	 * 비어있으면 전부 null(라인 정보 없음)로 채운다. 합계가 unit_count 와 다르면 파이썬 쪽
	 * 교차검증을 통과 못 했을 리 없지만, 시스템 경계라 방어적으로 한 번 더 막는다.
	 */
	List<BillOfLadingExtractionResponse.CargoLine> flattenCargoLines(
			List<BillOfLadingExtractionResponse.CargoLine> cargoLines, int totalCount) {
		if (cargoLines == null || cargoLines.isEmpty()) {
			List<BillOfLadingExtractionResponse.CargoLine> flat = new ArrayList<>(totalCount);
			for (int i = 0; i < totalCount; i++) {
				flat.add(null);
			}
			return flat;
		}

		List<BillOfLadingExtractionResponse.CargoLine> flat = new ArrayList<>(totalCount);
		for (BillOfLadingExtractionResponse.CargoLine line : cargoLines) {
			int lineCount = line.unitCount() != null ? line.unitCount() : 0;
			for (int i = 0; i < lineCount; i++) {
				flat.add(line);
			}
		}
		if (flat.size() != totalCount) {
			throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID,
					"cargo_lines 합계(%d)가 unit_count(%d)와 다릅니다.".formatted(flat.size(), totalCount));
		}
		return flat;
	}

	private List<String> nullList(int count) {
		List<String> list = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			list.add(null);
		}
		return list;
	}

	public record Result(String blNumber, int vehicleCount, List<String> vehicleIds) {
	}
}
