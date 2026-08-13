package com.Chung_Woon.Chung_Woon.ai.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 파이썬 {@code /internal/extract/bl} 이 실제로 내려주는 snake_case JSON을 역직렬화했을 때
 * 필드가 제대로 채워지는지 확인한다. {@code ObjectMapper} 는 앱의 전역 설정
 * ({@code spring.jackson.property-naming-strategy=LOWER_CAMEL_CASE}, application.yaml)과
 * 똑같이 맞춰서, 이 DTO의 {@code @JsonNaming} 오버라이드가 전역 설정과 충돌하지 않는지도 같이 본다.
 *
 * <p>스프링 컨텍스트 없이 순수 Jackson만으로 돈다 — {@code AiClient} 가 RestClient 로 이 DTO 를
 * 받는 경로에서 실제 장애(NoClassDefFoundError)가 났던 지점이라 회귀 테스트로 남겨둔다.
 */
class BillOfLadingExtractionResponseTest {

	private final ObjectMapper objectMapper = new ObjectMapper()
			.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);

	private static final String DOC01_JSON = """
			{
			  "bl_number": "NXR-USN-NTD-26081101",
			  "document_type": "BILL_OF_LADING",
			  "booking_number": "NXR-SP2608E-001",
			  "lot_code": "EV-A-0811",
			  "linked_route_code": null,
			  "vessel_name": "MV SYNTH PACIFIC",
			  "voyage_number": "SP2608E",
			  "port_of_loading": "KRUSN",
			  "port_of_discharge": "USNTD",
			  "issue_date": null,
			  "shipper_name": null,
			  "consignee_name": null,
			  "notify_party": null,
			  "cargo_lines": [],
			  "unit_count": 60,
			  "gross_weight_kg": null,
			  "measurement_cbm": null,
			  "vin_range_from": "SYNT26E0000000001",
			  "vin_range_to": "SYNT26E0000000060",
			  "powertrain": "BATTERY_EV",
			  "driveable_count": 60,
			  "tow_count": 0,
			  "tow_unit_numbers": [],
			  "unloading_priority": "P2",
			  "target_yard_zone": "EV-A / ROWS 01-06",
			  "discharge_seq_from": 41,
			  "discharge_seq_to": 100,
			  "special_handling": null,
			  "confidence": 0.0
			}
			""";

	@Test
	void deserializesSnakeCaseFieldsIntoTheDto() throws Exception {
		BillOfLadingExtractionResponse response =
				objectMapper.readValue(DOC01_JSON, BillOfLadingExtractionResponse.class);

		assertThat(response.blNumber()).isEqualTo("NXR-USN-NTD-26081101");
		assertThat(response.unitCount()).isEqualTo(60);
		assertThat(response.vinRangeFrom()).isEqualTo("SYNT26E0000000001");
		assertThat(response.dischargeSeqFrom()).isEqualTo(41);
		assertThat(response.dischargeSeqTo()).isEqualTo(100);
	}
}
