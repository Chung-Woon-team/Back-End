package com.Chung_Woon.Chung_Woon.domain.instruction;

import com.Chung_Woon.Chung_Woon.ai.AiClient;
import com.Chung_Woon.Chung_Woon.ai.dto.ParseInstructionRequest;
import com.Chung_Woon.Chung_Woon.ai.dto.ParseInstructionResponse;
import com.Chung_Woon.Chung_Woon.domain.vehicle.Vehicle;
import com.Chung_Woon.Chung_Woon.domain.vehicle.VehicleRepository;
import com.Chung_Woon.Chung_Woon.domain.yard.Block;
import com.Chung_Woon.Chung_Woon.domain.yard.BlockRepository;
import com.Chung_Woon.Chung_Woon.global.config.AiClientConfig;
import com.Chung_Woon.Chung_Woon.global.error.BusinessException;
import com.Chung_Woon.Chung_Woon.global.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 자연어 지시 → AI 파싱 → {@link PlanConstraint} 저장.
 *
 * <p>두 단계로 나뉜다(docs/API_CONTRACT.md 2절 "지시 · 제약"):
 * <ol>
 *   <li>{@link #createInstruction} — 원문만 저장하고 instruction_id 발급 (AI 호출 없음)</li>
 *   <li>{@link #parseAndSaveConstraints} — 저장된 원문을 AI 로 보내 파싱하고, 결과를
 *       {@link PlanConstraint}(항상 PENDING_REVIEW)로 저장</li>
 * </ol>
 * React 는 파이썬을 직접 못 부르니, valid_block_ids/brands/zones 는 스프링이 자기 DB(Block,
 * Vehicle)에서 직접 모아 AI 에 넘긴다 — 파이썬은 뭐가 유효한 값인지 알 방법이 없다.
 */
@Service
@RequiredArgsConstructor
public class InstructionParsingService {

	private final AiClient aiClient;
	private final InstructionRepository instructionRepository;
	private final PlanConstraintRepository planConstraintRepository;
	private final BlockRepository blockRepository;
	private final VehicleRepository vehicleRepository;
	private final TransactionTemplate transactionTemplate;

	@Qualifier(AiClientConfig.AI_OBJECT_MAPPER)
	private final ObjectMapper aiObjectMapper;

	@Transactional
	public InstructionSummary createInstruction(String rawText, String author) {
		Instruction instruction = Instruction.builder()
				.instructionId(nextInstructionId())
				.rawText(rawText)
				.author(author)
				.build();
		instructionRepository.save(instruction);
		return new InstructionSummary(instruction.getInstructionId());
	}

	/**
	 * AI 호출은 트랜잭션 <b>밖</b>이다. 파싱은 최대 30초까지 걸리는데(AiClientConfig 의 read
	 * timeout 은 60초), 트랜잭션 안에서 부르면 그동안 DB 커넥션을 잡고 있는다. prod 풀 크기가
	 * 작을 땐 지시 몇 건만 동시에 들어와도 앱 전체가 커넥션을 못 얻는다 — "파이썬이 죽어 있어도
	 * 조회 API 는 살아 있어야 한다"(API_CONTRACT.md)를 정면으로 깬다. 그래서 저장만 트랜잭션으로
	 * 감싼다(BillOfLadingExpansionService 와 같은 패턴).
	 */
	public ParseOutcome parseAndSaveConstraints(String instructionId) {
		Instruction instruction = instructionRepository.findById(instructionId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "지시를 찾을 수 없습니다: " + instructionId));

		ParseInstructionResponse response = aiClient.parseInstruction(new ParseInstructionRequest(
				instruction.getRawText(),
				instruction.getAuthor(),
				instruction.getInstructionId(),
				LocalDateTime.now(),
				validBlockIds(),
				validBrands(),
				validZones()
		));

		ParseInstructionResponse.Result result = response.result();
		if (result == null) {
			throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID, "파싱 결과가 비어있습니다.");
		}

		return transactionTemplate.execute(status -> persist(instructionId, response.threadId(), result));
	}

	private ParseOutcome persist(String instructionId, String threadId, ParseInstructionResponse.Result result) {
		Instruction instruction = instructionRepository.findById(instructionId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "지시를 찾을 수 없습니다: " + instructionId));

		List<String> constraintIds = attachConstraints(instruction, result.constraints());
		List<String> unresolved = result.unresolved() != null ? result.unresolved() : List.of();
		boolean requiresConfirmation = Boolean.TRUE.equals(result.requiresConfirmation());

		instruction.applyParseResult(threadId, writeJson(unresolved), requiresConfirmation);

		return new ParseOutcome(instructionId, constraintIds, unresolved, requiresConfirmation);
	}

	private List<String> attachConstraints(Instruction instruction, List<ParseInstructionResponse.ParsedConstraint> parsed) {
		if (parsed == null || parsed.isEmpty()) {
			return List.of();
		}
		// 전역 유일해야 하는 3자리 ID(C-001..C-999) 라 **지금까지 발급된 최대 번호** 다음부터 이어 붙인다.
		long startingOffset = nextConstraintNumber() - 1;
		List<String> constraintIds = new ArrayList<>(parsed.size());
		for (int i = 0; i < parsed.size(); i++) {
			ParseInstructionResponse.ParsedConstraint c = parsed.get(i);
			ParseInstructionResponse.TimeWindow window = c.timeWindow();
			String constraintId = "C-%03d".formatted(startingOffset + i + 1);

			PlanConstraint constraint = PlanConstraint.builder()
					.constraintId(constraintId)
					.type(c.type())
					.priority(c.priority())
					.targetJson(writeJson(c.target()))
					.valueJson(writeJson(c.value()))
					.windowStart(window != null ? window.start() : null)
					.windowEnd(window != null ? window.end() : null)
					.confidence(c.confidence() != null ? c.confidence() : 0.0)
					.build();

			instruction.addConstraint(constraint);
			constraintIds.add(constraintId);
		}
		return constraintIds;
	}

	private List<String> validBlockIds() {
		return blockRepository.findAll().stream().map(Block::getBlockId).toList();
	}

	private List<String> validZones() {
		return blockRepository.findAll().stream()
				.map(Block::getZoneCode)
				.filter(Objects::nonNull)
				.distinct()
				.toList();
	}

	private List<String> validBrands() {
		return vehicleRepository.findAll().stream()
				.map(Vehicle::getBrand)
				.filter(Objects::nonNull)
				.distinct()
				.toList();
	}

	/**
	 * {@code count()} 를 쓰면 안 된다 — 행이 한 번이라도 삭제되면 이미 쓰인 번호를 다시 내주고,
	 * PK 가 수동 할당이라 저장이 merge(UPSERT)로 동작해서 기존 행을 예외 없이 덮어쓴다
	 * (BillOfLadingExpansionService 에서 실측된 결함과 같은 패턴).
	 */
	private String nextInstructionId() {
		long n = instructionRepository.findMaxInstructionId()
				.map(id -> Long.parseLong(id.substring(id.indexOf('-') + 1)) + 1)
				.orElse(1L);
		return "INS-%03d".formatted(n);
	}

	private long nextConstraintNumber() {
		return planConstraintRepository.findMaxConstraintId()
				.map(id -> Long.parseLong(id.substring(id.indexOf('-') + 1)) + 1)
				.orElse(1L);
	}

	/** target/value 는 AI 가 준 snake_case 모양 그대로 저장한다(docs/DOMAIN.md 예시와 맞추기 위함). */
	private String writeJson(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof List<?> list && list.isEmpty()) {
			return null;
		}
		try {
			return aiObjectMapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID, "AI 응답을 JSON으로 저장하지 못했습니다.");
		}
	}

	/**
	 * 공개 API 응답이라 snake_case 로 나가야 한다(API_CONTRACT.md 규칙 1, FRONTEND_CONTRACT.md 규칙 2).
	 * 전역 Jackson 설정이 LOWER_CAMEL_CASE 라 이 애노테이션이 없으면 프론트가 읽는 필드가 전부 undefined 다.
	 */
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record InstructionSummary(String instructionId) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record ParseOutcome(
			String instructionId,
			List<String> constraintIds,
			List<String> unresolved,
			boolean requiresConfirmation
	) {
	}
}
