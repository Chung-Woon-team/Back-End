package com.Chung_Woon.Chung_Woon.domain.billoflading;

import com.Chung_Woon.Chung_Woon.ai.dto.BillOfLadingExtractionResponse;
import com.Chung_Woon.Chung_Woon.global.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 순수 로직만 검증한다(스프링 컨텍스트·DB 없이) — VIN 전개, SEQ 배분, cargo_lines 평탄화,
 * tow_unit_numbers 기반 driveable 판정. 필드가 실제 이미지에서 어떻게 나오는지는
 * ai/docs/HANDOFF_AI.md 의 DOC01~03 샘플을 근거로 한다.
 */
class BillOfLadingExpansionServiceTest {

	private final BillOfLadingExpansionService service =
			new BillOfLadingExpansionService(null, null, null);

	@Test
	void expandsVinsByIncrementingTheTrailingDigits() {
		List<String> vins = service.expandVins("SYNT26E0000000001", 3);

		assertThat(vins).containsExactly(
				"SYNT26E0000000001", "SYNT26E0000000002", "SYNT26E0000000003");
	}

	@Test
	void keepsTheDigitWidthOfVinRangeFromEvenWhenItsShorterThanExpected() {
		// 실측 사례: vin_range_from 이 예시 문서보다 0 하나 적게 나온 적이 있다.
		List<String> vins = service.expandVins("SYNT26E000000058", 2);

		assertThat(vins).containsExactly("SYNT26E000000058", "SYNT26E000000059");
	}

	@Test
	void returnsAllNullWhenVinRangeIsMissing() {
		assertThat(service.expandVins(null, 3)).containsExactly(null, null, null);
	}

	@Test
	void expandsDischargeSequenceSequentially() {
		assertThat(service.expandSequence(41, 4)).containsExactly(41, 42, 43, 44);
	}

	@Test
	void expandSequenceIsAllNullWhenFromIsMissing() {
		assertThat(service.expandSequence(null, 2)).containsExactly(null, null);
	}

	@Test
	void resolvesDriveableFromTowUnitNumbersWhenCargoLineDoesNotSayOtherwise() {
		Set<Integer> tow = Set.of(17, 31);

		assertThat(service.resolveDriveable(null, tow, 17)).isFalse();
		assertThat(service.resolveDriveable(null, tow, 31)).isFalse();
		assertThat(service.resolveDriveable(null, tow, 1)).isTrue();
	}

	@Test
	void cargoLineDriveableOverridesTowUnitNumbers() {
		var line = new BillOfLadingExtractionResponse.CargoLine(
				"ELECTRIC CITY BUSES", 10, null, null, 3.25, true);

		// 이 유닛이 tow 목록에 있어도, 품목 줄에 명시된 값이 우선한다.
		assertThat(service.resolveDriveable(line, Set.of(1), 1)).isTrue();
	}

	@Test
	void flattensCargoLinesInOrder() {
		var buses = new BillOfLadingExtractionResponse.CargoLine(
				"ELECTRIC CITY BUSES", 2, null, null, 3.25, true);
		var trucks = new BillOfLadingExtractionResponse.CargoLine(
				"HEAVY ELECTRIC TRUCKS", 1, null, null, 3.80, true);

		List<BillOfLadingExtractionResponse.CargoLine> flat =
				service.flattenCargoLines(List.of(buses, trucks), 3);

		assertThat(flat).containsExactly(buses, buses, trucks);
	}

	@Test
	void flattenCargoLinesIsAllNullWhenEmpty() {
		assertThat(service.flattenCargoLines(List.of(), 2)).containsExactly(null, null);
	}

	@Test
	void flattenCargoLinesRejectsCountMismatch() {
		var buses = new BillOfLadingExtractionResponse.CargoLine(
				"ELECTRIC CITY BUSES", 2, null, null, 3.25, true);

		assertThatThrownBy(() -> service.flattenCargoLines(List.of(buses), 5))
				.isInstanceOf(BusinessException.class);
	}
}
