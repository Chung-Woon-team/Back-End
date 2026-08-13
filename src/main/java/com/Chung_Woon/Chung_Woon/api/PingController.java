package com.Chung_Woon.Chung_Woon.api;

import com.Chung_Woon.Chung_Woon.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/** 서버가 떴는지, 어떤 프로필로 떴는지 눈으로 확인하는 용도. */
@Tag(name = "헬스체크", description = "서버가 살아있는지 확인")
@RestController
@RequestMapping("/api")
public class PingController {

	@Value("${spring.profiles.active:local}")
	private String activeProfile;

	@Operation(summary = "서버 상태 확인",
			description = "DB 없이도 응답한다. profile 로 어느 환경인지(local/dev/prod), "
					+ "server_time 으로 서버 시각을 확인할 수 있다.")
	@GetMapping("/ping")
	public ApiResponse<PingResponse> ping() {
		return ApiResponse.ok(new PingResponse("pong", activeProfile, LocalDateTime.now()));
	}

	public record PingResponse(String message, String profile, LocalDateTime serverTime) {
	}
}
