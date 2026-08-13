package com.Chung_Woon.Chung_Woon.api;

import com.Chung_Woon.Chung_Woon.api.dto.OccupancyCheckResponse;
import com.Chung_Woon.Chung_Woon.api.dto.OccupancyConfirmRequest;
import com.Chung_Woon.Chung_Woon.api.dto.OccupancyConfirmResponse;
import com.Chung_Woon.Chung_Woon.domain.observation.ObservationSource;
import com.Chung_Woon.Chung_Woon.domain.yard.YardOccupancyReviewService;
import com.Chung_Woon.Chung_Woon.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 야드 사진 한 장으로 현장과 DB 를 맞춘다. 2단계: 사진을 올려 차이를 보고({@code /check}),
 * 그 차이를 어떻게 반영할지 고른다({@code /check/{batch_id}/confirm}).
 */
@Tag(name = "야드", description = "야드 격자와 점유 상태")
@RestController
@RequestMapping("/api/yard/occupancy/check")
@RequiredArgsConstructor
public class YardOccupancyReviewController {

	private final YardOccupancyReviewService yardOccupancyReviewService;

	@Operation(summary = "야드 사진 → 지금 DB 와 대조",
			description = """
					야드 **전체**를 담은 사진 한 장을 올리면(블록 4개 따로 아님), AI 가 340개 슬롯 각각을
					비었다/찼다로 읽어서 지금 Slot.status 와 대조한다. 사진은 슬롯별 점유 여부만 알려줄 뿐
					**어느 차인지는 모른다** — 그건 이 단계도 다음 확정 단계도 알아내지 않는다.

					- multipart/form-data 의 file 파트로 이미지를 보낸다 (최대 20MB)
					- source_type 은 FIXED_CAMERA / DRONE / MANUAL 중 하나, 생략하면 MANUAL
					- 이미지 처리라 **10~30초** 걸릴 수 있다
					- 이 단계는 **아직 DB 를 바꾸지 않는다.** 응답의 batch_id 를 들고 있다가
					  /confirm 을 불러야 실제로 반영된다
					- diff_count 가 0 이면 사진과 현장이 일치하는 것 - 확정을 부를 필요가 없다

					**슬롯별 상세 목록은 안 준다** - diff_count 로 몇 곳이 다른지만 보고 확정 여부를
					정하면 된다. 실제로 어느 슬롯이 어떻게 바뀌는지는 /confirm 이 내부적으로 계산해서
					적용하고, 굳이 API 로 노출하지 않는다.

					⚠️ AI 에 Gemini 키가 없거나 인식이 실패하면 200 으로 confidence 0.0 · 전부 EMPTY 인
					**고정 더미**가 온다. 이 경우 diff_count 는 "지금 DB 에서 찬 걸로 돼 있는 슬롯 수"로
					나오니, 확정 전에 반드시 confidence 를 보고 requires_confirmation 이 true 인지
					확인할 것. GET /api/dashboard 의 ai_engine.status 가 DEGRADED 인지도 참고.
					""")
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<OccupancyCheckResponse> check(
			@RequestParam(defaultValue = "MANUAL") ObservationSource sourceType,
			@RequestParam("file") MultipartFile file) {
		return ApiResponse.ok(yardOccupancyReviewService.checkOccupancy(file, sourceType));
	}

	@Operation(summary = "대조 결과 확정",
			description = """
					check 가 돌려준 batch_id 로, 사람이 고른 대로 반영한다.

					- `PHOTO`: 사진 버전으로 DB 를 바꾼다. 새로 찬 슬롯은 Slot.status 만 OCCUPIED 로
					  바뀌고 **어떤 차량과도 연결되지 않는다**(사진만으로는 어느 차인지 알 수 없어서).
					  새로 빈 슬롯은 EMPTY 로 바뀌고, 거기 서 있던 걸로 돼 있던 차량이 있으면
					  그 차의 자리 정보도 같이 뗀다
					- `KEEP`: 아무것도 바꾸지 않는다 (applied_count = 0)

					반영할 목록은 이 호출 **시점에 DB 와 다시 대조**해서 계산한다 - check 응답을 그대로
					믿고 반영하지 않는다. 사진을 올린 뒤 확정하기 전 사이에 다른 경로(다른 담당자의
					승인 등)로 DB 가 바뀌었을 수 있어서다.

					⚠️ batch_id 가 없는 값이면 404 (C003) 다.
					""")
	@PostMapping("/{batchId}/confirm")
	public ApiResponse<OccupancyConfirmResponse> confirm(
			@PathVariable String batchId,
			@RequestBody OccupancyConfirmRequest request) {
		return ApiResponse.ok(yardOccupancyReviewService.confirm(batchId, request.choice()));
	}
}
