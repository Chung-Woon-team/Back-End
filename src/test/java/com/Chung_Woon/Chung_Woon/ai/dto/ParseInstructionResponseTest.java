package com.Chung_Woon.Chung_Woon.ai.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 파이썬 {@code /internal/parse} 가 실제로 내려주는 snake_case JSON을 역직렬화했을 때 필드가
 * 제대로 채워지는지 확인한다. B/L 작업 때 {@code RestClient} 경로에서 실제로 전 필드가 null로
 * 떨어지는 장애가 났던 지점이라(전역 카멜케이스 매퍼가 이 DTO의 snake_case 오버라이드를 무시함)
 * 회귀 테스트로 남겨둔다.
 */
class ParseInstructionResponseTest {

	private final ObjectMapper objectMapper = new ObjectMapper()
			.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
			.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.findAndRegisterModules();

	private static final String RESPONSE_JSON = """
			{
			  "thread_id": "th_a1b2c3d4",
			  "result": {
			    "instruction_id": "INS-001",
			    "constraints": [
			      {
			        "constraint_id": "C-001",
			        "type": "BLOCK_CLOSURE",
			        "target": { "block_ids": ["B02"], "attribute": null, "values": null,
			                    "vehicle_ids": null, "filter": null },
			        "value": null,
			        "time_window": { "start": "2026-08-13T14:00:00", "end": null },
			        "priority": "HARD",
			        "confidence": 0.99
			      }
			    ],
			    "unresolved": ["가까이"],
			    "requires_confirmation": true
			  }
			}
			""";

	@Test
	void deserializesSnakeCaseFieldsIgnoringConstraintId() throws Exception {
		ParseInstructionResponse response =
				objectMapper.readValue(RESPONSE_JSON, ParseInstructionResponse.class);

		assertThat(response.threadId()).isEqualTo("th_a1b2c3d4");
		assertThat(response.result().instructionId()).isEqualTo("INS-001");
		assertThat(response.result().unresolved()).containsExactly("가까이");
		assertThat(response.result().requiresConfirmation()).isTrue();

		var constraint = response.result().constraints().get(0);
		assertThat(constraint.type().name()).isEqualTo("BLOCK_CLOSURE");
		assertThat(constraint.target().blockIds()).containsExactly("B02");
		assertThat(constraint.priority().name()).isEqualTo("HARD");
		assertThat(constraint.confidence()).isEqualTo(0.99);
		assertThat(constraint.timeWindow().start()).isEqualTo(java.time.LocalDateTime.parse("2026-08-13T14:00:00"));
	}
}
