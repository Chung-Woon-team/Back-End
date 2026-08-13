package com.Chung_Woon.Chung_Woon.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cloud Run 콘솔에서 복사한 URL 은 끝에 슬래시가 붙는다. 그대로 baseUrl 이 되면
 * 요청 경로가 {@code //internal/...} 이 돼서 배포 환경에서만 404 가 난다.
 */
class AiClientConfigTest {

	@Test
	@DisplayName("끝 슬래시를 떼어낸다")
	void stripsTrailingSlash() {
		assertThat(AiClientConfig.normalizeBaseUrl("https://ai.example.run.app/"))
				.isEqualTo("https://ai.example.run.app");
		assertThat(AiClientConfig.normalizeBaseUrl("https://ai.example.run.app///"))
				.isEqualTo("https://ai.example.run.app");
	}

	@Test
	@DisplayName("슬래시가 없으면 그대로 둔다")
	void keepsUrlWithoutTrailingSlash() {
		assertThat(AiClientConfig.normalizeBaseUrl("http://localhost:8000"))
				.isEqualTo("http://localhost:8000");
	}

	@Test
	@DisplayName("앞뒤 공백도 정리한다")
	void stripsWhitespace() {
		assertThat(AiClientConfig.normalizeBaseUrl("  https://ai.example.run.app/  "))
				.isEqualTo("https://ai.example.run.app");
	}
}
