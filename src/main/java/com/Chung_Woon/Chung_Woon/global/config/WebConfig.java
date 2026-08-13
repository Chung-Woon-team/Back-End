package com.Chung_Woon.Chung_Woon.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 프론트(로컬 dev 서버 / 배포 도메인)에서 API 를 부를 수 있게 CORS 를 연다.
 * 배포 도메인이 정해지면 allowedOriginPatterns 에 추가할 것.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
				.allowedOriginPatterns(
						"http://localhost:*",
						"http://127.0.0.1:*",
						"https://*.vercel.app",
						"https://*.run.app")
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
				.allowedHeaders("*")
				.exposedHeaders("Authorization")
				.allowCredentials(true)
				.maxAge(3600);
	}
}
