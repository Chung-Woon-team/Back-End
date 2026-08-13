package com.Chung_Woon.Chung_Woon.domain.plan;

import com.Chung_Woon.Chung_Woon.ai.AiClient;
import com.Chung_Woon.Chung_Woon.ai.dto.ReplanRequest;
import com.Chung_Woon.Chung_Woon.ai.dto.ReplanResponse;
import com.Chung_Woon.Chung_Woon.domain.instruction.ConstraintPriority;
import com.Chung_Woon.Chung_Woon.domain.instruction.ConstraintStatus;
import com.Chung_Woon.Chung_Woon.domain.instruction.ConstraintType;
import com.Chung_Woon.Chung_Woon.domain.instruction.InstructionRepository;
import com.Chung_Woon.Chung_Woon.domain.instruction.PlanConstraint;
import com.Chung_Woon.Chung_Woon.domain.instruction.PlanConstraintRepository;
import com.Chung_Woon.Chung_Woon.domain.vehicle.Vehicle;
import com.Chung_Woon.Chung_Woon.domain.vehicle.VehicleRepository;
import com.Chung_Woon.Chung_Woon.domain.yard.Block;
import com.Chung_Woon.Chung_Woon.domain.yard.Slot;
import com.Chung_Woon.Chung_Woon.domain.yard.SlotRepository;
import com.Chung_Woon.Chung_Woon.domain.yard.SlotStatus;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DB 없이 순수 로직만: 제약 → 요청 페이로드 변환, 응답 → Placement/MoveTask 매핑.
 * AiClient·리포지토리는 전부 Mockito 로 대체한다(InstructionParsingServiceTest 와 같은 패턴).
 */
class ReplanExecutionServiceTest {

	private final AiClient aiClient = mock(AiClient.class);
	private final YardQueryService yardQueryService = mock(YardQueryService.class);
	private final PlanConstraintRepository planConstraintRepository = mock(PlanConstraintRepository.class);
	private final PlanRevisionRepository planRevisionRepository = mock(PlanRevisionRepository.class);
	private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
	private final SlotRepository slotRepository = mock(SlotRepository.class);
	private final InstructionRepository instructionRepository = mock(InstructionRepository.class);
	private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

	private ReplanExecutionService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		ObjectMapper aiObjectMapper = new ObjectMapper()
				.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		service = new ReplanExecutionService(
				aiClient, yardQueryService, planConstraintRepository, planRevisionRepository,
				vehicleRepository, slotRepository, instructionRepository, transactionTemplate, aiObjectMapper);

		when(yardQueryService.currentYardState()).thenReturn(
				new ReplanRequest.YardState(List.of(), List.of(), Map.of()));
		when(yardQueryService.plannableVehicles()).thenReturn(List.of());
		when(planConstraintRepository.findApplicable()).thenReturn(List.of());

		when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<Object> callback = invocation.getArgument(0);
			return callback.doInTransaction(null);
		});

		Block block = Block.builder().blockId("B01").closed(false).build();
		when(slotRepository.getReferenceById(any())).thenAnswer(invocation ->
				Slot.builder().slotId(invocation.getArgument(0)).block(block).status(SlotStatus.EMPTY).build());
		when(vehicleRepository.getReferenceById(any())).thenAnswer(invocation ->
				Vehicle.builder().vehicleId(invocation.getArgument(0)).build());
	}

	@Test
	void persistsPlacementsAndMoveTasksFromTheAiResponse() {
		var path = List.of(new ReplanResponse.PathStep(3, 4, 0), new ReplanResponse.PathStep(4, 4, 1));
		var move = new ReplanResponse.Move("V-0001", null, "B01-R04-C04", 1, "신규 배치", 30.0, path);
		var kpi = new ReplanResponse.PlanKpi(30.0, 0.0, 0, 1, 0.0, 500L);
		var response = new ReplanResponse(
				"B0-r1", null, Map.of("V-0001", "B01-R04-C04"), List.of(move), kpi, null, List.of());
		when(aiClient.replan(any())).thenReturn(response);

		var result = service.executeReplan(null, null);

		assertThat(result.planVersion()).isEqualTo("B0-r1");
		assertThat(result.placementCount()).isEqualTo(1);
		assertThat(result.moveCount()).isEqualTo(1);
		assertThat(result.unplaced()).isEmpty();
		// path 는 DB 엔 안 남지만(MoveTask 에 컬럼 없음), 이 응답에는 그대로 실려 나가야 한다.
		assertThat(result.moves()).hasSize(1);
		assertThat(result.moves().get(0).path()).containsExactlyElementsOf(path);
	}

	@Test
	void carriesUnplacedThroughEvenThoughItIsNotPersisted() {
		var kpi = new ReplanResponse.PlanKpi(0.0, 0.0, 1, 0, 100.0, 10L);
		var response = new ReplanResponse("B0-r1", null, Map.of(), List.of(), kpi, null, List.of("V-0002"));
		when(aiClient.replan(any())).thenReturn(response);

		var result = service.executeReplan(null, null);

		assertThat(result.unplaced()).containsExactly("V-0002");
	}

	@Test
	void convertsApprovedConstraintsWithStoredJsonIntoTheAiRequestPayload() {
		PlanConstraint constraint = PlanConstraint.builder()
				.constraintId("C-001")
				.type(ConstraintType.BLOCK_CLOSURE)
				.priority(ConstraintPriority.HARD)
				.targetJson("{\"block_ids\":[\"B01\"]}")
				.confidence(0.99)
				.status(ConstraintStatus.APPROVED)
				.build();
		when(planConstraintRepository.findApplicable()).thenReturn(List.of(constraint));
		var kpi = new ReplanResponse.PlanKpi(0.0, 0.0, 0, 0, 100.0, 10L);
		when(aiClient.replan(any())).thenReturn(
				new ReplanResponse("B0-r1", null, Map.of(), List.of(), kpi, null, List.of()));

		service.executeReplan(null, null);

		var captor = org.mockito.ArgumentCaptor.forClass(ReplanRequest.class);
		org.mockito.Mockito.verify(aiClient).replan(captor.capture());
		var payload = captor.getValue().constraints().get(0);
		assertThat(payload.constraintId()).isEqualTo("C-001");
		assertThat(payload.target().get("block_ids").get(0).asText()).isEqualTo("B01");
	}
}
