package com.Chung_Woon.Chung_Woon.api;

import com.Chung_Woon.Chung_Woon.api.dto.YardSeedRequest;
import com.Chung_Woon.Chung_Woon.domain.yard.YardSeedService;
import com.Chung_Woon.Chung_Woon.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 데모용 야드 점유 상태 주입.
 *
 * <p><b>기본은 꺼져 있다.</b> {@code demo.seed-api.enabled=true}
 * (환경변수 {@code DEMO_SEED_API_ENABLED=true}) 일 때만 이 컨트롤러가 등록된다.
 * 켜져 있지 않으면 경로 자체가 없어서 404 다.
 *
 * <p>이 프로젝트는 아직 인증이 없다({@code SecurityConfig} 가 전부 permitAll).
 * 이 엔드포인트는 {@code reset: true} 로 <b>차량 데이터를 전부 지울 수 있으므로</b>,
 * 데이터를 넣을 때만 잠깐 켜고 바로 다시 끄는 것을 전제로 만들었다.
 */
@Tag(name = "야드 (데모)", description = "데모용 점유 상태 주입 — 기본 꺼짐")
@RestController
@RequestMapping("/api/yard/seed")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "demo.seed-api.enabled", havingValue = "true")
public class YardSeedController {

	private final YardSeedService yardSeedService;

	@Operation(summary = "야드 점유 상태 주입 (데모용)",
			description = """
					실제 야드 사진에서 읽은 점유 상태를 넣는다. 격자(블록 4개 · 슬롯 340칸)는
					서버가 기동할 때 이미 만들어 두므로 여기서는 **점유 상태만** 채운다.

					- 차는 **통로에 가까운 자리(depth 0)부터** 들어간다
					- reset: true 면 기존 차량을 지우고 새로 넣는다. **멱등하다** —
					  숫자를 고쳐가며 여러 번 돌려도 두 배가 되지 않는다
					- 블록 용량(85)을 넘기거나 없는 블록을 주면 400

					🔒 DEMO_SEED_API_ENABLED=true 일 때만 열린다. 꺼져 있으면 404 다.
					""")
	@PostMapping
	public ApiResponse<YardSeedRequest.Result> seed(@RequestBody YardSeedRequest request) {
		return ApiResponse.ok(yardSeedService.seed(request));
	}

	/**
	 * 차량을 전부 지우고 야드를 빈 상태로 되돌린다. 격자(블록 4개 · 슬롯 1,936칸)는 남는다.
	 *
	 * <p>되돌릴 수 없다. 지우기 전에 무엇이 지워지는지 응답의 {@code vehicles_removed} 로 확인할 것.
	 */
	@Operation(summary = "야드 초기화 (데모용)",
			description = "차량을 전부 지우고 슬롯 점유·블록 폐쇄까지 되돌린다. 격자는 남는다. "
					+ "**되돌릴 수 없다** — 응답의 vehicles_removed 로 무엇이 지워졌는지 확인할 것.")
	@DeleteMapping
	public ApiResponse<YardSeedRequest.ClearResult> clear() {
		return ApiResponse.ok(yardSeedService.clearAll());
	}
}
