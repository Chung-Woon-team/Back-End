package com.Chung_Woon.Chung_Woon.domain.plan;

import com.Chung_Woon.Chung_Woon.ai.AiClient;
import com.Chung_Woon.Chung_Woon.ai.dto.BriefRequest;
import com.Chung_Woon.Chung_Woon.ai.dto.BriefResponse;
import com.Chung_Woon.Chung_Woon.api.dto.PlanBriefingResponse;
import com.Chung_Woon.Chung_Woon.domain.vehicle.Vehicle;
import com.Chung_Woon.Chung_Woon.domain.yard.Block;
import com.Chung_Woon.Chung_Woon.domain.yard.Slot;
import com.Chung_Woon.Chung_Woon.global.error.BusinessException;
import com.Chung_Woon.Chung_Woon.global.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * confirmations 4종 규칙과 파이썬 페이로드 조립만 본다. DB·스프링 컨텍스트 없이
 * Mockito mock 으로 조립한다(ReplanExecutionServiceTest 와 같은 패턴).
 */
class PlanBriefingServiceTest {

	private final PlanRevisionRepository planRevisionRepository = mock(PlanRevisionRepository.class);
	private final MoveTaskRepository moveTaskRepository = mock(MoveTaskRepository.class);
	private final AiClient aiClient = mock(AiClient.class);
	private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

	private PlanBriefingService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		// AiClientConfig.aiObjectMapper 와 같은 설정. 메타 JSON 이 snake_case + non_null 로 저장돼야
		// GET 이 그대로 되읽을 수 있다.
		ObjectMapper aiObjectMapper = JsonMapper.builder()
				.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
				.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
				.serializationInclusion(JsonInclude.Include.NON_NULL)
				.build()
				.findAndRegisterModules();

		service = new PlanBriefingService(planRevisionRepository, moveTaskRepository,
				new PlanVersionResolver(planRevisionRepository), aiClient, transactionTemplate, aiObjectMapper);

		when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<Object> callback = invocation.getArgument(0);
			return callback.doInTransaction(null);
		});
		when(moveTaskRepository.findByPlanRevision_PlanVersionOrderBySequenceAsc(any()))
				.thenReturn(List.of());
	}

	private static PlanKpi kpi(Double avg, Double rehandle, Integer hard, Integer changed,
			Double retention, Long millis) {
		return PlanKpi.builder()
				.avgMoveDistance(avg).rehandleProxy(rehandle).hardViolations(hard)
				.changedVehicles(changed).planRetentionRate(retention).calcMillis(millis)
				.build();
	}

	private PlanRevision given(String planVersion, String basedOn, PlanKpi planKpi) {
		PlanRevision revision = PlanRevision.builder()
				.planVersion(planVersion).basedOnVersion(basedOn).status(PlanStatus.DRAFT)
				.kpi(planKpi).build();
		when(planRevisionRepository.findByPlanVersion(planVersion)).thenReturn(Optional.of(revision));
		return revision;
	}

	private static MoveTask move(String vehicleId, String toSlotId, int sequence, Double distance) {
		Block block = Block.builder().blockId("B03").closed(false).build();
		return MoveTask.builder()
				.vehicle(Vehicle.builder().vehicleId(vehicleId).build())
				.toSlot(Slot.builder().slotId(toSlotId).block(block).build())
				.sequence(sequence)
				.reason("B02 블록 폐쇄로 인한 재배치")
				.distanceMeters(distance)
				.build();
	}

	private void aiReturns(String briefing, String source) {
		when(aiClient.brief(any())).thenReturn(new BriefResponse(briefing, List.of(), source));
	}

	// ------------------------------------------------------------------ 생성

	@Test
	@DisplayName("정상 생성이면 문장과 메타가 판에 저장되고 GET 과 같은 모양으로 나온다")
	void generatesAndPersists() {
		PlanRevision before = PlanRevision.builder().planVersion("B0-r1")
				.kpi(kpi(812.0, 1.93, 2, 61, 86.9, 2110L)).build();
		when(planRevisionRepository.findByPlanVersion("B0-r1")).thenReturn(Optional.of(before));
		PlanRevision revision = given("B0-r2", "B0-r1", kpi(502.0, 1.24, 0, 42, 91.6, 1740L));
		aiReturns("B02 블록 폐쇄로 42대가 재배치되었습니다.\n- 평균 이동거리: 812 → 502m (38% 감소)", "AI");

		PlanBriefingResponse response = service.generate("B0-r2");

		assertThat(response.planVersion()).isEqualTo("B0-r2");
		assertThat(response.state()).isEqualTo("GENERATED");
		assertThat(response.stateLabel()).isEqualTo("생성됨");
		assertThat(response.source()).isEqualTo("AI");
		assertThat(response.sourceLabel()).isEqualTo("AI 생성");
		assertThat(response.generatedAt()).isNotNull();
		assertThat(response.briefing()).startsWith("B02 블록 폐쇄로 42대가 재배치되었습니다.");

		assertThat(revision.getBriefing()).isEqualTo(response.briefing());
		assertThat(revision.getConsistencyIssuesJson())
				.contains("\"source\":\"AI\"")
				.contains("\"generated_at\"");
	}

	@Test
	@DisplayName("파이썬이 FALLBACK 이라고 하면 그대로 표시한다 — Gemini 없이도 200 이다")
	void keepsFallbackSourceFromPython() {
		given("B0-r2", null, kpi(502.0, 1.24, 0, 42, 91.6, 1740L));
		aiReturns("42대가 재배치되었습니다.", "FALLBACK");

		PlanBriefingResponse response = service.generate("B0-r2");

		assertThat(response.source()).isEqualTo("FALLBACK");
		assertThat(response.sourceLabel()).isEqualTo("수치 기반 자동 작성");
	}

	@Test
	@DisplayName("KPI 6개 중 하나라도 없으면 파이썬을 부르기 전에 400 으로 막는다")
	void rejectsIncompleteKpiBeforeCallingPython() {
		// 파이썬 PlanKpi 는 extra="forbid" + 6필드 필수라 null 을 보내면 422 로 튕긴다.
		given("B0-r0a0b0c", null, kpi(502.0, 1.24, 0, 42, 91.6, null));

		assertThatThrownBy(() -> service.generate("B0-r0a0b0c"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("KPI 가 없는 판은 브리핑을 만들 수 없습니다: B0-r0a0b0c")
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_INPUT);

		verify(aiClient, never()).brief(any());
	}

	@Test
	@DisplayName("KPI 임베디드가 통째로 없어도 같은 400 이다")
	void rejectsMissingKpiEntirely() {
		given("B0-r0", null, null);

		assertThatThrownBy(() -> service.generate("B0-r0")).isInstanceOf(BusinessException.class);
		verify(aiClient, never()).brief(any());
	}

	@Test
	@DisplayName("파이썬이 200 을 줬는데 문장이 비어 있으면 502 다")
	void blankBriefingIsBadGateway() {
		given("B0-r2", null, kpi(502.0, 1.24, 0, 42, 91.6, 1740L));
		aiReturns("   ", "AI");

		assertThatThrownBy(() -> service.generate("B0-r2"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("브리핑 문장을 받지 못했습니다.")
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.AI_RESPONSE_INVALID);
	}

	@Test
	@DisplayName("파이썬이 죽어 있으면 AiClient 의 503 이 그대로 올라간다 (여기서 문장을 지어내지 않는다)")
	void propagatesAiUnavailable() {
		given("B0-r2", null, kpi(502.0, 1.24, 0, 42, 91.6, 1740L));
		when(aiClient.brief(any()))
				.thenThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE, "브리핑 서비스를 호출하지 못했습니다."));

		assertThatThrownBy(() -> service.generate("B0-r2"))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.AI_UNAVAILABLE);
	}

	// --------------------------------------------------------- confirmations

	@Test
	@DisplayName("Hard 위반이 남아 있으면 HARD_VIOLATION 경고를 만든다")
	void hardViolationConfirmation() {
		PlanRevision before = PlanRevision.builder().planVersion("B0-r1")
				.kpi(kpi(812.0, 1.93, 0, 61, 86.9, 2110L)).build();
		when(planRevisionRepository.findByPlanVersion("B0-r1")).thenReturn(Optional.of(before));
		given("B0-r2", "B0-r1", kpi(502.0, 1.24, 3, 42, 91.6, 1740L));
		aiReturns("문장", "AI");

		var confirmations = service.generate("B0-r2").confirmations();

		assertThat(confirmations).hasSize(1);
		assertThat(confirmations.get(0).code()).isEqualTo("HARD_VIOLATION");
		assertThat(confirmations.get(0).severity()).isEqualTo("WARN");
		assertThat(confirmations.get(0).message()).isEqualTo("Hard 제약 위반 3건이 남아 있습니다.");
		assertThat(confirmations.get(0).actionHint()).isEqualTo("미배치 차량을 수동으로 배정한 뒤 승인하세요.");
		assertThat(confirmations.get(0).slotId()).isNull();
	}

	@Test
	@DisplayName("유지율이 80% 밑이면 RETENTION_DROP 경고를 만든다 (80.0 은 경고 아님)")
	void retentionDropConfirmation() {
		PlanRevision before = PlanRevision.builder().planVersion("B0-r1")
				.kpi(kpi(812.0, 1.93, 0, 61, 86.9, 2110L)).build();
		when(planRevisionRepository.findByPlanVersion("B0-r1")).thenReturn(Optional.of(before));
		given("B0-r2", "B0-r1", kpi(502.0, 1.24, 0, 42, 72.35, 1740L));
		aiReturns("문장", "AI");

		var confirmations = service.generate("B0-r2").confirmations();

		assertThat(confirmations).hasSize(1);
		assertThat(confirmations.get(0).code()).isEqualTo("RETENTION_DROP");
		// fmt() 는 round1 후 소수부가 0 이면 정수로 적는다. 72.35 → 72.4
		assertThat(confirmations.get(0).message())
				.isEqualTo("계획 유지율이 72.4% 로 낮습니다. 이전 판 대비 자리를 지킨 차량이 적습니다.");

		given("B0-r3", "B0-r1", kpi(502.0, 1.24, 0, 42, 80.0, 1740L));
		assertThat(service.generate("B0-r3").confirmations()).isEmpty();
	}

	@Test
	@DisplayName("평균의 2배를 넘는 이동은 LONG_MOVE 로, 먼 순서대로 최대 3건만 만든다")
	void longMoveConfirmationsAreCappedAndSorted() {
		PlanRevision before = PlanRevision.builder().planVersion("B0-r1")
				.kpi(kpi(812.0, 1.93, 0, 61, 86.9, 2110L)).build();
		when(planRevisionRepository.findByPlanVersion("B0-r1")).thenReturn(Optional.of(before));
		given("B0-r2", "B0-r1", kpi(502.0, 1.24, 0, 42, 91.6, 1740L));
		when(moveTaskRepository.findByPlanRevision_PlanVersionOrderBySequenceAsc("B0-r2")).thenReturn(List.of(
				move("V-0182", "B03-R12-C04", 1, 1284.0),
				move("V-0183", "B03-R12-C05", 2, 468.0),   // 평균 이하 - 제외
				move("V-0184", "B03-R12-C06", 3, 1004.0),  // 정확히 2배 - 초과가 아니라 제외
				move("V-0185", "B03-R12-C07", 4, 2000.0),
				move("V-0186", "B03-R12-C08", 5, 1500.0),
				move("V-0187", "B03-R12-C09", 6, 1100.0)   // 4번째로 멀다 - 잘린다
		));
		aiReturns("문장", "AI");

		var confirmations = service.generate("B0-r2").confirmations();

		assertThat(confirmations).hasSize(3);
		assertThat(confirmations).allSatisfy(c -> assertThat(c.code()).isEqualTo("LONG_MOVE"));
		assertThat(confirmations).extracting(PlanBriefingResponse.Confirmation::slotId)
				.containsExactly("B03-R12-C07", "B03-R12-C08", "B03-R12-C04");
		assertThat(confirmations.get(2).message())
				.isEqualTo("V-0182 의 이동거리가 1284m 로 이 판 평균(502m)의 2배를 넘습니다.");
		assertThat(confirmations.get(2).actionHint()).isEqualTo("더 가까운 대체 슬롯이 있는지 확인하세요.");
	}

	@Test
	@DisplayName("이전 판이 없으면 NO_BASELINE 을 INFO 로 알린다 — 없는 숫자를 지어내지 않는다")
	void noBaselineConfirmation() {
		given("B0-r1c2d34", null, kpi(812.0, 1.93, 0, 61, 86.9, 1740L));
		aiReturns("문장", "AI");

		var confirmations = service.generate("B0-r1c2d34").confirmations();

		assertThat(confirmations).hasSize(1);
		assertThat(confirmations.get(0).code()).isEqualTo("NO_BASELINE");
		assertThat(confirmations.get(0).severity()).isEqualTo("INFO");
		assertThat(confirmations.get(0).message()).isEqualTo("비교할 이전 판이 없어 전후 비교를 표시하지 않습니다.");
		assertThat(confirmations.get(0).actionHint()).isNull();
	}

	@Test
	@DisplayName("이전 판 KPI 가 불완전하면 kpi_before 를 통째로 뺀다 (부분 전송 금지)")
	void incompletePreviousKpiIsNotSentAsBefore() {
		// 파이썬 PlanKpi 는 extra="forbid" + 6필드 필수라 부분적으로 채워진 kpi_before 는 422 로 튕긴다.
		PlanRevision before = PlanRevision.builder().planVersion("B0-r1")
				.kpi(kpi(812.0, null, 0, 61, 86.9, 2110L)).build();
		when(planRevisionRepository.findByPlanVersion("B0-r1")).thenReturn(Optional.of(before));
		given("B0-r2", "B0-r1", kpi(502.0, 1.24, 0, 42, 91.6, 1740L));
		aiReturns("문장", "AI");

		var response = service.generate("B0-r2");

		ArgumentCaptor<BriefRequest> captor = ArgumentCaptor.forClass(BriefRequest.class);
		verify(aiClient).brief(captor.capture());
		assertThat(captor.getValue().kpiBefore()).isNull();

		// NO_BASELINE 은 붙지 않는다 — 규칙 조건이 "이전 판 kpi == null" 이지 "불완전" 이 아니다.
		// 이전 판은 실재하고 KPI 화면은 나머지 지표를 정상적으로 비교하므로, 여기서 "비교할 판이
		// 없다" 고 말하면 같은 판의 두 화면이 서로 다른 말을 하게 된다.
		assertThat(response.confirmations()).isEmpty();
	}

	@Test
	@DisplayName("규칙 4종의 평가 순서가 고정이다")
	void confirmationOrderIsFixed() {
		given("B0-r2", null, kpi(502.0, 1.24, 2, 42, 55.0, 1740L));
		when(moveTaskRepository.findByPlanRevision_PlanVersionOrderBySequenceAsc("B0-r2"))
				.thenReturn(List.of(move("V-0182", "B03-R12-C04", 1, 1284.0)));
		aiReturns("문장", "AI");

		assertThat(service.generate("B0-r2").confirmations())
				.extracting(PlanBriefingResponse.Confirmation::code)
				.containsExactly("HARD_VIOLATION", "RETENTION_DROP", "LONG_MOVE", "NO_BASELINE");
	}

	// ------------------------------------------------------------- 페이로드

	@Test
	@DisplayName("moves 는 200건까지만 싣는다 — 파이썬은 개수를 세지 않는다")
	void movePayloadIsCappedAt200() {
		given("B0-r2", null, kpi(502.0, 1.24, 0, 500, 91.6, 1740L));
		List<MoveTask> many = new ArrayList<>();
		for (int i = 0; i < 500; i++) {
			many.add(move("V-%04d".formatted(i), "B03-R12-C04", i + 1, 10.0));
		}
		when(moveTaskRepository.findByPlanRevision_PlanVersionOrderBySequenceAsc("B0-r2")).thenReturn(many);
		aiReturns("문장", "AI");

		service.generate("B0-r2");

		ArgumentCaptor<BriefRequest> captor = ArgumentCaptor.forClass(BriefRequest.class);
		verify(aiClient).brief(captor.capture());
		assertThat(captor.getValue().moves()).hasSize(200);
		// 앞에서부터 자른다(sequence asc).
		assertThat(captor.getValue().moves().get(0).vehicleId()).isEqualTo("V-0000");
		// 대수의 정본은 잘린 배열이 아니라 KPI 다.
		assertThat(captor.getValue().kpi().changedVehicles()).isEqualTo(500);
	}

	@Test
	@DisplayName("consistency_issues 로 자바가 만든 confirmations 를 그대로 실어 보낸다")
	void sendsJavaBuiltConfirmationsAsConsistencyIssues() {
		given("B0-r2", null, kpi(502.0, 1.24, 1, 42, 91.6, 1740L));
		aiReturns("문장", "AI");

		service.generate("B0-r2");

		ArgumentCaptor<BriefRequest> captor = ArgumentCaptor.forClass(BriefRequest.class);
		verify(aiClient).brief(captor.capture());
		assertThat(captor.getValue().consistencyIssues())
				.extracting(BriefRequest.ConsistencyIssue::code)
				.containsExactly("HARD_VIOLATION", "NO_BASELINE");
	}

	@Test
	@DisplayName("파이썬이 돌려준 confirmations 는 버린다 — 자바가 만든 목록이 유일한 정본이다")
	void ignoresConfirmationsEchoedByPython() {
		given("B0-r2", null, kpi(502.0, 1.24, 0, 42, 91.6, 1740L));
		when(aiClient.brief(any())).thenReturn(new BriefResponse("문장",
				List.of(java.util.Map.of("code", "MADE_UP_BY_PYTHON", "message", "무시돼야 한다")), "AI"));

		assertThat(service.generate("B0-r2").confirmations())
				.extracting(PlanBriefingResponse.Confirmation::code)
				.containsExactly("NO_BASELINE");
	}

	// ------------------------------------------------------------------ 조회

	@Test
	@DisplayName("생성 후 조회하면 저장된 메타에서 source·generated_at·confirmations 를 되읽는다")
	void loadReadsBackTheStoredSnapshot() {
		PlanRevision revision = given("B0-r2", null, kpi(502.0, 1.24, 2, 42, 91.6, 1740L));
		aiReturns("문장", "FALLBACK");
		PlanBriefingResponse generated = service.generate("B0-r2");

		PlanBriefingResponse loaded = service.load("B0-r2");

		// POST 응답과 GET 응답이 어긋나면 안 된다 - 그래서 조회 시점에 다시 계산하지 않는다.
		assertThat(loaded).isEqualTo(generated);
		assertThat(loaded.source()).isEqualTo("FALLBACK");
		assertThat(loaded.confirmations()).extracting(PlanBriefingResponse.Confirmation::code)
				.containsExactly("HARD_VIOLATION", "NO_BASELINE");
		assertThat(revision.getBriefing()).isEqualTo("문장");
	}

	@Test
	@DisplayName("아직 생성 전이면 404 가 아니라 NOT_GENERATED 로 200 이다")
	void loadOnUngeneratedPlanIsNotAnError() {
		given("B0-r0a0b0c", null, null);

		PlanBriefingResponse response = service.load("B0-r0a0b0c");

		assertThat(response.state()).isEqualTo("NOT_GENERATED");
		assertThat(response.stateLabel()).isEqualTo("생성 전");
		assertThat(response.briefing()).isNull();
		assertThat(response.source()).isNull();
		assertThat(response.generatedAt()).isNull();
		assertThat(response.confirmations()).isEmpty();
	}

	@Test
	@DisplayName("메타 JSON 이 깨져 있어도 500 이 아니라 문장만 내려준다")
	void brokenMetaDoesNotBreakTheScreen() {
		PlanRevision revision = given("B0-r2", null, kpi(502.0, 1.24, 0, 42, 91.6, 1740L));
		revision.attachBriefing("문장");
		revision.attachBriefingMeta("{ 이건 JSON 이 아니다");

		PlanBriefingResponse response = service.load("B0-r2");

		assertThat(response.state()).isEqualTo("GENERATED");
		assertThat(response.briefing()).isEqualTo("문장");
		assertThat(response.confirmations()).isEmpty();
		assertThat(response.source()).isNull();
	}

	@Test
	@DisplayName("조회는 파이썬을 절대 부르지 않는다")
	void loadNeverCallsPython() {
		given("B0-r2", null, kpi(502.0, 1.24, 0, 42, 91.6, 1740L));

		service.load("B0-r2");

		verify(aiClient, never()).brief(any());
	}
}
