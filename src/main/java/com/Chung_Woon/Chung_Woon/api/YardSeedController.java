package com.Chung_Woon.Chung_Woon.api;

import com.Chung_Woon.Chung_Woon.api.dto.YardSeedRequest;
import com.Chung_Woon.Chung_Woon.domain.yard.YardSeedService;
import com.Chung_Woon.Chung_Woon.global.common.ApiResponse;
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
@RestController
@RequestMapping("/api/yard/seed")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "demo.seed-api.enabled", havingValue = "true")
public class YardSeedController {

	private final YardSeedService yardSeedService;

	@PostMapping
	public ApiResponse<YardSeedRequest.Result> seed(@RequestBody YardSeedRequest request) {
		return ApiResponse.ok(yardSeedService.seed(request));
	}

	/**
	 * 차량을 전부 지우고 야드를 빈 상태로 되돌린다. 격자(블록 4개 · 슬롯 1,936칸)는 남는다.
	 *
	 * <p>되돌릴 수 없다. 지우기 전에 무엇이 지워지는지 응답의 {@code vehicles_removed} 로 확인할 것.
	 */
	@DeleteMapping
	public ApiResponse<YardSeedRequest.ClearResult> clear() {
		return ApiResponse.ok(yardSeedService.clearAll());
	}
}
