package com.Chung_Woon.Chung_Woon.api;

import com.Chung_Woon.Chung_Woon.global.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/** 서버가 떴는지, 어떤 프로필로 떴는지 눈으로 확인하는 용도. */
@RestController
@RequestMapping("/api")
public class PingController {

	@Value("${spring.profiles.active:local}")
	private String activeProfile;

	@GetMapping("/ping")
	public ApiResponse<PingResponse> ping() {
		return ApiResponse.ok(new PingResponse("pong", activeProfile, LocalDateTime.now()));
	}

	public record PingResponse(String message, String profile, LocalDateTime serverTime) {
	}
}
