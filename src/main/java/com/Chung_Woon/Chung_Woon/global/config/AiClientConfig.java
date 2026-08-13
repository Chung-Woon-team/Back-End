package com.Chung_Woon.Chung_Woon.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 파이썬 AI 서비스(FastAPI, :8000)를 부르는 RestClient.
 *
 * <p>docs/API_CONTRACT.md 의 권장 타임아웃을 따른다 — {@code /internal/extract/*} 는
 * 이미지 처리라 60초까지 걸릴 수 있다.
 *
 * <p>전용 ObjectMapper 를 쓴다 — 앱 전역 ObjectMapper 는 프론트용 LOWER_CAMEL_CASE 가 기본이라,
 * {@link com.Chung_Woon.Chung_Woon.ai.dto.BillOfLadingExtractionResponse} 의
 * {@code @JsonNaming} 클래스 오버라이드가 {@code RestClient} 의 기본 컨버터를 통해서는
 * 무시되는 문제가 있었다(전 필드 null 로 역직렬화됨). 이 클라이언트는 파이썬만 상대하고
 * 파이썬은 100% snake_case 라, 아예 매퍼 기본값을 snake_case 로 고정해서 우회한다.
 *
 * <p>Spring Boot 가 기본으로 끼워주는 JSON 컨버터(신규 {@link JacksonJsonHttpMessageConverter}
 * 또는 구형 {@link MappingJackson2HttpMessageConverter}, 둘 다 전역 LOWER_CAMEL_CASE 매퍼를 씀)를
 * 반드시 다 걷어내고 이 전용 컨버터를 맨 앞에 꽂아야 한다 — 그냥 뒤에 추가만 하면 기본 컨버터가
 * 먼저 매칭돼서 무시된다(실제로 겪은 버그).
 */
@Configuration
public class AiClientConfig {

	@Bean
	public RestClient aiRestClient(@Value("${ai.base-url}") String aiBaseUrl) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
		requestFactory.setReadTimeout((int) Duration.ofSeconds(60).toMillis());

		ObjectMapper aiObjectMapper = JsonMapper.builder()
				.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
				.build()
				.findAndRegisterModules();

		return RestClient.builder()
				.baseUrl(aiBaseUrl)
				.requestFactory(requestFactory)
				.messageConverters(converters -> {
					converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter
							|| c instanceof JacksonJsonHttpMessageConverter);
					converters.add(0, new MappingJackson2HttpMessageConverter(aiObjectMapper));
				})
				.build();
	}
}
