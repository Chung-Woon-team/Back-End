package com.Chung_Woon.Chung_Woon.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 프론트(로컬 dev 서버 / 배포 도메인)에서 API 를 부를 수 있게 CORS 를 연다.
 *
 * <p>배포된 프론트 주소를 명시해 뒀다. {@code https://*.run.app} 패턴으로도 이미 통과하지만,
 * 나중에 와일드카드를 좁힐 때 프론트가 조용히 막히는 걸 막으려는 것이다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	/** 배포된 프론트엔드(Cloud Run). */
	private static final String FRONTEND_URL =
			"https://autoyard-copilot-front-145786632792.asia-northeast3.run.app";

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
				.allowedOriginPatterns(
						FRONTEND_URL,
						"http://localhost:*",
						"http://127.0.0.1:*",
						"https://*.run.app")
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
				.allowedHeaders("*")
				.exposedHeaders("Authorization")
				.allowCredentials(true)
				.maxAge(3600);
	}
}
