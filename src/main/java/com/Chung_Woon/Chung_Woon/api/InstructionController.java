package com.Chung_Woon.Chung_Woon.api;

import com.Chung_Woon.Chung_Woon.domain.instruction.InstructionParsingService;
import com.Chung_Woon.Chung_Woon.global.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지시 원문 저장과 AI 파싱을 두 단계로 연다(docs/API_CONTRACT.md 2절).
 *
 * <p>React 가 파이썬을 직접 못 부르니, 두 번째 단계({@code /constraints})가 내부적으로
 * {@code /internal/parse} 를 호출한다 — 문서 초안의 요청 바디 예시(constraints 를 클라이언트가
 * 채워 보내는 모양)는 실제로는 불가능해서(React 가 AI 응답을 만들 수 없음), 이 엔드포인트가
 * 파싱까지 하고 결과를 응답으로 돌려주는 걸로 구현했다.
 */
@Tag(name = "지시", description = "현장 자연어 지시 접수와 AI 파싱")
@RestController
@RequestMapping("/api/instructions")
@RequiredArgsConstructor
public class InstructionController {

	private final InstructionParsingService instructionParsingService;

	@Operation(summary = "지시 등록",
			description = """
					현장에서 말로 준 지시를 **원문 그대로** 저장하고 instruction_id(INS-001)를 발급한다.
					여기서는 AI 를 부르지 않는다 — 파싱은 다음 단계다.

					원문을 보관하는 이유는 파싱이 틀렸을 때 대조할 근거가 필요해서다.
					""")
	@PostMapping
	public ApiResponse<InstructionParsingService.InstructionSummary> create(
			@RequestBody @Valid CreateInstructionRequest request) {
		return ApiResponse.ok(instructionParsingService.createInstruction(request.rawText(), request.author()));
	}

	@Operation(summary = "지시 파싱 → 제약 생성",
			description = """
					저장된 지시를 AI(Gemini)에 보내 구조화된 제약으로 바꾼다. **여기서 AI 가 돈다.**

					- 요청 바디가 없다. curl 로 부를 때는 빈 바디(--data 빈문자열)를 붙여야 한다
					  (없으면 Content-Length 가 안 붙어 Cloud Run 이 411 로 끊는다)
					- 첫 호출은 **10~30초** 걸릴 수 있다 (콜드스타트 + Gemini)
					- 만들어진 제약은 전부 PENDING_REVIEW 다. **승인 전에는 아무것도 반영되지 않는다**
					- requires_confirmation 이 true 면 해석 못 한 표현이 있다는 뜻이다.
					  unresolved 에 그 표현이 담긴다 — 화면에서 담당자에게 되물어야 한다
					""")
	@PostMapping("/{id}/constraints")
	public ApiResponse<InstructionParsingService.ParseOutcome> parseConstraints(@PathVariable("id") String id) {
		return ApiResponse.ok(instructionParsingService.parseAndSaveConstraints(id));
	}

	public record CreateInstructionRequest(
			@NotBlank String rawText,
			String author
	) {
	}
}
