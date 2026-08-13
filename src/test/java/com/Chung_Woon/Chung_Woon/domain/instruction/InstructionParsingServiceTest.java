package com.Chung_Woon.Chung_Woon.domain.instruction;

import com.Chung_Woon.Chung_Woon.ai.AiClient;
import com.Chung_Woon.Chung_Woon.ai.dto.ParseInstructionRequest;
import com.Chung_Woon.Chung_Woon.ai.dto.ParseInstructionResponse;
import com.Chung_Woon.Chung_Woon.domain.vehicle.VehicleRepository;
import com.Chung_Woon.Chung_Woon.domain.yard.BlockRepository;
import com.Chung_Woon.Chung_Woon.global.error.BusinessException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DB/AI 없이 순수 로직만: constraint_id 채번, target/value snake_case JSON 직렬화,
 * unresolved/requires_confirmation 반영. 리포지토리·AiClient 는 전부 Mockito 로 대체한다.
 *
 * <p>{@code transactionTemplate.execute(...)} 는 실제 트랜잭션 없이 콜백만 바로 실행하도록
 * 스텁한다 — 여기서 보는 건 트랜잭션 동작이 아니라 그 안의 순수 로직이다.
 */
class InstructionParsingServiceTest {

	private final AiClient aiClient = mock(AiClient.class);
	private final InstructionRepository instructionRepository = mock(InstructionRepository.class);
	private final PlanConstraintRepository planConstraintRepository = mock(PlanConstraintRepository.class);
	private final BlockRepository blockRepository = mock(BlockRepository.class);
	private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
	private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

	private InstructionParsingService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		ObjectMapper aiObjectMapper = new ObjectMapper()
				.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
				.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
		service = new InstructionParsingService(
				aiClient, instructionRepository, planConstraintRepository,
				blockRepository, vehicleRepository, transactionTemplate, aiObjectMapper);

		when(blockRepository.findAll()).thenReturn(List.of());
		when(vehicleRepository.findAll()).thenReturn(List.of());
		when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<Object> callback = invocation.getArgument(0);
			return callback.doInTransaction(null);
		});
	}

	@Test
	void createInstructionIssuesSequentialIdBasedOnMaxExistingId() {
		when(instructionRepository.findMaxInstructionId()).thenReturn(Optional.of("INS-041"));

		var summary = service.createInstruction("3번 블록 폐쇄해", "야드관리자A");

		assertThat(summary.instructionId()).isEqualTo("INS-042");
	}

	@Test
	void createInstructionStartsAtOneWhenNoneExist() {
		when(instructionRepository.findMaxInstructionId()).thenReturn(Optional.empty());

		var summary = service.createInstruction("3번 블록 폐쇄해", "야드관리자A");

		assertThat(summary.instructionId()).isEqualTo("INS-001");
	}

	@Test
	void parseAndSaveAssignsSequentialConstraintIdsAndStoresSnakeCaseJson() {
		Instruction instruction = Instruction.builder()
				.instructionId("INS-001")
				.rawText("3번 블록 폐쇄해")
				.build();
		when(instructionRepository.findById("INS-001")).thenReturn(Optional.of(instruction));
		when(planConstraintRepository.findMaxConstraintId()).thenReturn(Optional.of("C-005"));

		var target = new ParseInstructionResponse.Target(List.of("B03"), null, null, null, null);
		var constraint = new ParseInstructionResponse.ParsedConstraint(
				ConstraintType.BLOCK_CLOSURE, target, null,
				new ParseInstructionResponse.TimeWindow(LocalDateTime.parse("2026-08-13T14:00:00"), null),
				ConstraintPriority.HARD, 0.99);
		var result = new ParseInstructionResponse.Result(
				"INS-001", List.of(constraint), List.of("가까이"), true);
		when(aiClient.parseInstruction(any())).thenReturn(new ParseInstructionResponse("th_abc123", result));

		var outcome = service.parseAndSaveConstraints("INS-001");

		assertThat(outcome.constraintIds()).containsExactly("C-006");
		assertThat(outcome.unresolved()).containsExactly("가까이");
		assertThat(outcome.requiresConfirmation()).isTrue();

		assertThat(instruction.getConstraints()).hasSize(1);
		PlanConstraint saved = instruction.getConstraints().get(0);
		assertThat(saved.getConstraintId()).isEqualTo("C-006");
		assertThat(saved.getTargetJson()).isEqualTo("{\"block_ids\":[\"B03\"]}");
		assertThat(saved.getStatus()).isEqualTo(ConstraintStatus.PENDING_REVIEW);
		assertThat(instruction.getThreadId()).isEqualTo("th_abc123");
		assertThat(instruction.isRequiresConfirmation()).isTrue();
	}

	@Test
	void throwsAiResponseInvalidWhenResultIsMissing() {
		Instruction instruction = Instruction.builder().instructionId("INS-001").rawText("x").build();
		when(instructionRepository.findById("INS-001")).thenReturn(Optional.of(instruction));
		when(aiClient.parseInstruction(any())).thenReturn(new ParseInstructionResponse("th_x", null));

		assertThatThrownBy(() -> service.parseAndSaveConstraints("INS-001"))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void passesDbDerivedValidListsToAi() {
		Instruction instruction = Instruction.builder().instructionId("INS-001").rawText("x").build();
		when(instructionRepository.findById("INS-001")).thenReturn(Optional.of(instruction));
		when(aiClient.parseInstruction(any())).thenReturn(new ParseInstructionResponse(
				"th_x", new ParseInstructionResponse.Result("INS-001", List.of(), List.of(), false)));

		service.parseAndSaveConstraints("INS-001");

		var captor = org.mockito.ArgumentCaptor.forClass(ParseInstructionRequest.class);
		org.mockito.Mockito.verify(aiClient).parseInstruction(captor.capture());
		assertThat(captor.getValue().instructionId()).isEqualTo("INS-001");
		assertThat(captor.getValue().rawText()).isEqualTo("x");
	}
}
