package com.Chung_Woon.Chung_Woon.api;

import com.Chung_Woon.Chung_Woon.domain.yard.YardGrid;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 대시보드 페이로드가 계약대로 나가는지. 특히 <b>필드명이 snake_case 인지</b>를 본다 —
 * record 에 {@code @JsonNaming} 만 붙였을 때 실제 응답은 camelCase 로 나가는 사고가 있었다.
 */
@SpringBootTest
class DashboardControllerTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private ObjectMapper objectMapper;

	private JsonNode fetchDashboard() throws Exception {
		MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		String body = mockMvc.perform(get("/api/dashboard"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		JsonNode root = objectMapper.readTree(body);
		assertThat(root.get("success").asBoolean()).isTrue();
		return root.get("data");
	}

	@Test
	@DisplayName("응답 필드명이 전부 snake_case 다")
	void fieldsAreSnakeCase() throws Exception {
		JsonNode data = fetchDashboard();

		assertThat(data.has("generated_at")).isTrue();
		assertThat(data.has("recent_activities")).isTrue();
		assertThat(data.has("recent_instructions")).isTrue();
		assertThat(data.has("ai_engine")).isTrue();

		JsonNode summary = data.get("summary");
		assertThat(summary.has("vehicles_in_yard")).isTrue();
		assertThat(summary.has("pending_constraints")).isTrue();
		assertThat(summary.has("available_slots")).isTrue();
		assertThat(summary.has("total_slots")).isTrue();
		assertThat(summary.has("occupancy_pct")).isTrue();

		// camelCase 가 하나라도 섞이면 프론트가 undefined 를 읽는다.
		assertThat(summary.has("vehiclesInYard")).isFalse();
		assertThat(data.has("generatedAt")).isFalse();
	}

	@Test
	@DisplayName("슬롯 총량이 도면과 같고, 빈 야드에서는 전부 배치 가능하다")
	void slotCountsMatchTheGrid() throws Exception {
		JsonNode summary = fetchDashboard().get("summary");

		assertThat(summary.get("total_slots").asLong()).isEqualTo(YardGrid.SLOT_COUNT);
		assertThat(summary.get("available_slots").asLong()).isEqualTo(YardGrid.SLOT_COUNT);
		assertThat(summary.get("occupancy_pct").asDouble()).isZero();
		assertThat(summary.get("congestion").asText()).isEqualTo("NORMAL");
	}

	@Test
	@DisplayName("블록 4개가 용량(85)과 함께 나온다")
	void blocksCarryCapacity() throws Exception {
		JsonNode blocks = fetchDashboard().get("blocks");

		assertThat(blocks).hasSize(YardGrid.BLOCK_COUNT);
		JsonNode first = blocks.get(0);
		assertThat(first.get("block_id").asText()).isEqualTo("B01");
		assertThat(first.get("capacity").asLong()).isEqualTo(YardGrid.SLOTS_PER_BLOCK);
		assertThat(first.get("status").asText()).isEqualTo("OPERATING");
		assertThat(first.get("status_label").asText()).isEqualTo("운영 중");
	}

	@Test
	@DisplayName("AI 가 안 떠 있어도 대시보드는 200 으로 뜬다")
	void survivesWhenAiIsDown() throws Exception {
		// 테스트 환경에는 파이썬이 없다. 그래도 화면이 떠야 한다.
		JsonNode ai = fetchDashboard().get("ai_engine");

		assertThat(ai.get("status").asText()).isIn("DOWN", "DEGRADED", "UP");
		assertThat(ai.get("status_label").asText()).isNotBlank();

		// 앱 전역이 default-property-inclusion: non_null 이라 null 필드는 키째로 빠진다.
		// AI 가 죽으면 gemini_enabled 를 알 수 없으므로 키가 없는 게 정상이다.
		// 프론트는 "값이 null" 이 아니라 "키가 없음" 을 다뤄야 한다.
		if ("DOWN".equals(ai.get("status").asText())) {
			assertThat(ai.has("gemini_enabled")).isFalse();
		} else {
			assertThat(ai.get("gemini_enabled").isBoolean()).isTrue();
		}
	}

	@Test
	@DisplayName("승인된 판이 없으면 평균 이동거리 키가 아예 빠진다")
	void omitsKpiWhenNoApprovedPlan() throws Exception {
		JsonNode summary = fetchDashboard().get("summary");

		// null 을 내보내는 게 아니라 키를 뺀다. 프론트가 optional 로 다뤄야 하는 지점.
		assertThat(summary.has("avg_move_distance_meters")).isFalse();
		assertThat(summary.has("avg_move_distance_delta_pct")).isFalse();

		// 반면 항상 값이 있는 것들은 빠지면 안 된다.
		assertThat(summary.get("vehicles_in_yard").isNumber()).isTrue();
		assertThat(summary.get("total_slots").isNumber()).isTrue();
	}
}
