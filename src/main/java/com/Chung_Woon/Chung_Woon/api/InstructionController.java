package com.Chung_Woon.Chung_Woon.api;

import com.Chung_Woon.Chung_Woon.domain.instruction.InstructionParsingService;
import com.Chung_Woon.Chung_Woon.global.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
@RestController
@RequestMapping("/api/instructions")
@RequiredArgsConstructor
public class InstructionController {

	private final InstructionParsingService instructionParsingService;

	@PostMapping
	public ApiResponse<InstructionParsingService.InstructionSummary> create(
			@RequestBody @Valid CreateInstructionRequest request) {
		return ApiResponse.ok(instructionParsingService.createInstruction(request.rawText(), request.author()));
	}

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
