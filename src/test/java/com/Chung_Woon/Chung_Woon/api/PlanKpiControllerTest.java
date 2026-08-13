package com.Chung_Woon.Chung_Woon.api;

import com.Chung_Woon.Chung_Woon.domain.plan.PlanKpi;
import com.Chung_Woon.Chung_Woon.domain.plan.PlanRevision;
import com.Chung_Woon.Chung_Woon.domain.plan.PlanRevisionRepository;
import com.Chung_Woon.Chung_Woon.domain.plan.PlanStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * KPI·브리핑 페이로드가 계약대로 나가는지. 특히 <b>필드명이 snake_case 인지</b>와
 * <b>값이 없으면 키가 통째로 빠지는지</b>를 본다 — 프론트 타입이 전부 옵셔널인 근거가 이것이다.
 *
 * <p>{@code @Transactional} 로 감싸 판을 심었다가 롤백한다. 안 그러면 여기서 심은 승인 판이
 * 컨텍스트를 공유하는 {@link DashboardControllerTest}("승인된 판이 없으면…")를 깨뜨린다.
 */
@SpringBootTest
@Transactional
class PlanKpiControllerTest {

	private static final String BASELINE = "B0-rTEST01";
	private static final String CURRENT = "B0-rTEST02";
	private static final String NO_KPI = "B0-rTEST03";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PlanRevisionRepository planRevisionRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

		LocalDateTime now = LocalDateTime.now();
		planRevisionRepository.save(PlanRevision.builder()
				.planVersion(BASELINE)
				.status(PlanStatus.APPROVED)
				.approvedBy("야드관리자A")
				.approvedAt(now.minusHours(2))
				.kpi(kpi(812.0, 1.93, 2, 61, 86.9, 2110L))
				.build());
		planRevisionRepository.save(PlanRevision.builder()
				.planVersion(CURRENT)
				.basedOnVersion(BASELINE)
				.status(PlanStatus.APPROVED)
				.approvedBy("야드관리자A")
				.approvedAt(now)
				.kpi(kpi(502.0, 1.24, 0, 42, 91.6, 1740L))
				.build());
		planRevisionRepository.save(PlanRevision.builder()
				.planVersion(NO_KPI)
				.status(PlanStatus.DRAFT)
				.build());
	}

	private static PlanKpi kpi(Double avg, Double rehandle, Integer hard, Integer changed,
			Double retention, Long millis) {
		return PlanKpi.builder()
				.avgMoveDistance(avg).rehandleProxy(rehandle).hardViolations(hard)
				.changedVehicles(changed).planRetentionRate(retention).calcMillis(millis)
				.build();
	}

	private JsonNode getData(String path) throws Exception {
		String body = mockMvc.perform(get(path))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		JsonNode root = objectMapper.readTree(body);
		assertThat(root.get("success").asBoolean()).isTrue();
		return root.get("data");
	}

	private static JsonNode metric(JsonNode data, String key) {
		for (JsonNode metric : data.get("metrics")) {
			if (key.equals(metric.get("key").asText())) {
				return metric;
			}
		}
		throw new AssertionError("metric 이 없다: " + key);
	}

	@Test
	@DisplayName("KPI 응답 필드명이 전부 snake_case 다")
	void kpiFieldsAreSnakeCase() throws Exception {
		JsonNode data = getData("/api/plans/" + CURRENT + "/kpi");

		assertThat(data.has("plan_version")).isTrue();
		assertThat(data.has("based_on_version")).isTrue();
		assertThat(data.has("status_label")).isTrue();
		assertThat(data.has("has_kpi")).isTrue();
		assertThat(data.has("unit_label")).isTrue();

		JsonNode distance = metric(data, "avg_move_distance");
		assertThat(distance.has("index_before")).isTrue();
		assertThat(distance.has("index_after")).isTrue();
		assertThat(distance.has("delta_abs")).isTrue();
		assertThat(distance.has("delta_pct")).isTrue();
		assertThat(distance.has("delta_label")).isTrue();
		assertThat(distance.has("unit_label")).isTrue();

		// camelCase 가 하나라도 섞이면 프론트가 undefined 를 읽는다.
		assertThat(data.has("planVersion")).isFalse();
		assertThat(data.has("basedOnVersion")).isFalse();
		assertThat(data.has("statusLabel")).isFalse();
		assertThat(data.has("hasKpi")).isFalse();
		assertThat(distance.has("indexAfter")).isFalse();
		assertThat(distance.has("indexBefore")).isFalse();
		assertThat(distance.has("deltaLabel")).isFalse();
		assertThat(distance.has("unitLabel")).isFalse();

		JsonNode highlight = data.get("highlights").get(0);
		assertThat(highlight.has("unit_label")).isTrue();
		assertThat(highlight.has("unitLabel")).isFalse();
	}

	@Test
	@DisplayName("이전 판이 있으면 계약의 예시값을 그대로 내려준다")
	void kpiCarriesDerivedValues() throws Exception {
		JsonNode data = getData("/api/plans/" + CURRENT + "/kpi");

		assertThat(data.get("plan_version").asText()).isEqualTo(CURRENT);
		assertThat(data.get("based_on_version").asText()).isEqualTo(BASELINE);
		assertThat(data.get("status").asText()).isEqualTo("APPROVED");
		assertThat(data.get("status_label").asText()).isEqualTo("승인됨");
		assertThat(data.get("comparable").asBoolean()).isTrue();
		assertThat(data.get("unit").asText()).isEqualTo("METER");

		JsonNode distance = metric(data, "avg_move_distance");
		assertThat(distance.get("label").asText()).isEqualTo("평균 이동거리");
		assertThat(distance.get("index_before").asDouble()).isEqualTo(100.0);
		assertThat(distance.get("index_after").asDouble()).isEqualTo(61.8);
		assertThat(distance.get("delta_label").asText()).isEqualTo("38% 감소");
		assertThat(distance.get("tone").asText()).isEqualTo("GOOD");

		assertThat(data.get("highlights")).hasSize(3);
		assertThat(data.get("highlights").get(0).get("value").asInt()).isZero();
		assertThat(data.get("highlights").get(0).get("tone").asText()).isEqualTo("GOOD");
	}

	@Test
	@DisplayName("비교 불가면 before·index_*·delta_* 키가 통째로 빠진다 (null 이 아니라 키가 없다)")
	void baselineOmitsComparisonKeysEntirely() throws Exception {
		JsonNode data = getData("/api/plans/" + BASELINE + "/kpi");

		assertThat(data.get("comparable").asBoolean()).isFalse();
		// baseline 이라 based_on_version 도 키째로 없다.
		assertThat(data.has("based_on_version")).isFalse();

		JsonNode distance = metric(data, "avg_move_distance");
		assertThat(distance.has("before")).isFalse();
		assertThat(distance.has("index_before")).isFalse();
		assertThat(distance.has("index_after")).isFalse();
		assertThat(distance.has("delta_abs")).isFalse();
		assertThat(distance.has("delta_pct")).isFalse();
		assertThat(distance.has("delta_label")).isFalse();

		// 반면 after·better·tone 은 항상 있어야 한다 - 막대 하나는 그려야 하니까.
		assertThat(distance.get("after").asDouble()).isEqualTo(812.0);
		assertThat(distance.get("better").asText()).isEqualTo("LOWER");
		assertThat(distance.get("tone").asText()).isEqualTo("NEUTRAL");
	}

	@Test
	@DisplayName("KPI 가 없는 판도 404 가 아니라 빈 배열로 200 이다")
	void planWithoutKpiStillReturns200() throws Exception {
		JsonNode data = getData("/api/plans/" + NO_KPI + "/kpi");

		assertThat(data.get("has_kpi").asBoolean()).isFalse();
		assertThat(data.get("metrics")).isEmpty();
		assertThat(data.get("highlights")).isEmpty();
		assertThat(data.get("status_label").asText()).isEqualTo("검토 전");
	}

	@Test
	@DisplayName("latest 별칭은 가장 최근 승인 판으로 해석되고, 응답엔 실제 판 번호가 담긴다")
	void latestAliasResolvesToTheNewestApprovedPlan() throws Exception {
		JsonNode data = getData("/api/plans/latest/kpi");

		assertThat(data.get("plan_version").asText()).isEqualTo(CURRENT);
	}

	@Test
	@DisplayName("없는 판은 404 C003 이다")
	void unknownPlanVersionIs404() throws Exception {
		String body = mockMvc.perform(get("/api/plans/B0-rZZZZZZ/kpi"))
				.andExpect(status().isNotFound())
				.andReturn().getResponse().getContentAsString();

		JsonNode root = objectMapper.readTree(body);
		assertThat(root.get("success").asBoolean()).isFalse();
		assertThat(root.get("error").get("code").asText()).isEqualTo("C003");
		assertThat(root.get("error").get("message").asText()).contains("B0-rZZZZZZ");
	}

	@Test
	@DisplayName("KPI 조회는 파이썬 없이도 200 이다")
	void kpiSurvivesWhenAiIsDown() throws Exception {
		// 테스트 환경에는 파이썬이 없다. 그래도 이 화면은 떠야 한다.
		assertThat(getData("/api/plans/" + CURRENT + "/kpi").get("has_kpi").asBoolean()).isTrue();
	}

	@Test
	@DisplayName("브리핑 조회는 생성 전이어도 200 이고 필드명은 snake_case 다")
	void briefingBeforeGenerationIsNotAnError() throws Exception {
		JsonNode data = getData("/api/plans/" + CURRENT + "/briefing");

		assertThat(data.get("plan_version").asText()).isEqualTo(CURRENT);
		assertThat(data.get("state").asText()).isEqualTo("NOT_GENERATED");
		assertThat(data.get("state_label").asText()).isEqualTo("생성 전");
		assertThat(data.get("confirmations")).isEmpty();

		// 생성 전이라 이 키들은 통째로 빠진다.
		assertThat(data.has("briefing")).isFalse();
		assertThat(data.has("source")).isFalse();
		assertThat(data.has("generated_at")).isFalse();

		assertThat(data.has("stateLabel")).isFalse();
		assertThat(data.has("planVersion")).isFalse();
	}

	@Test
	@DisplayName("파이썬이 죽어 있으면 브리핑 생성만 503 AI001 이고 조회는 계속 200 이다")
	void briefingGenerationIs503WhenPythonIsDown() throws Exception {
		String body = mockMvc.perform(post("/api/plans/" + CURRENT + "/briefing"))
				.andExpect(status().isServiceUnavailable())
				.andReturn().getResponse().getContentAsString();

		JsonNode root = objectMapper.readTree(body);
		assertThat(root.get("error").get("code").asText()).isEqualTo("AI001");
		assertThat(root.get("error").get("message").asText()).isEqualTo("브리핑 서비스를 호출하지 못했습니다.");

		// 파이썬이 죽어도 조회 API 는 그대로 살아 있다.
		assertThat(getData("/api/plans/" + CURRENT + "/briefing").get("state").asText())
				.isEqualTo("NOT_GENERATED");
	}

	@Test
	@DisplayName("KPI 가 없는 판에 브리핑 생성을 걸면 파이썬을 타기 전에 400 C001 이다")
	void briefingGenerationOnPlanWithoutKpiIs400() throws Exception {
		String body = mockMvc.perform(post("/api/plans/" + NO_KPI + "/briefing"))
				.andExpect(status().isBadRequest())
				.andReturn().getResponse().getContentAsString();

		JsonNode root = objectMapper.readTree(body);
		assertThat(root.get("error").get("code").asText()).isEqualTo("C001");
		assertThat(root.get("error").get("message").asText())
				.isEqualTo("KPI 가 없는 판은 브리핑을 만들 수 없습니다: " + NO_KPI);
	}
}
