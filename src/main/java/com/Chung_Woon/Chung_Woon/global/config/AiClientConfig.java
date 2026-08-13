package com.Chung_Woon.Chung_Woon.global.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Qualifier;
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
 * AI DTO 들의 {@code @JsonNaming} 클래스 오버라이드가 {@code RestClient} 의 기본 컨버터를
 * 통해서는 무시되는 문제가 있었다(전 필드 null 로 역직렬화됨). 이 클라이언트는 파이썬만
 * 상대하고 파이썬은 100% snake_case 라, 아예 매퍼 기본값을 snake_case 로 고정해서 우회한다.
 * {@code PlanConstraint.targetJson}/{@code valueJson} 도 원래 AI 가 준 snake_case 모양 그대로
 * DB 에 저장해야 해서(docs/DOMAIN.md 예시가 snake_case) 같은 매퍼를 재사용한다.
 *
 * <p>Spring Boot 가 기본으로 끼워주는 JSON 컨버터(신규 {@link JacksonJsonHttpMessageConverter}
 * 또는 구형 {@link MappingJackson2HttpMessageConverter}, 둘 다 전역 LOWER_CAMEL_CASE 매퍼를 씀)를
 * 반드시 다 걷어내고 이 전용 컨버터를 맨 앞에 꽂아야 한다 — 그냥 뒤에 추가만 하면 기본 컨버터가
 * 먼저 매칭돼서 무시된다(실제로 겪은 버그).
 */
@Configuration
public class AiClientConfig {

	public static final String AI_OBJECT_MAPPER = "aiObjectMapper";

	@Bean(AI_OBJECT_MAPPER)
	public ObjectMapper aiObjectMapper() {
		// FAIL_ON_UNKNOWN_PROPERTIES 를 꺼둔다 - 파이썬 응답에 이 DTO 가 안 받는 필드가 있어도
		// (예: constraint_id - 스프링이 자기 규칙으로 다시 채번해서 여기선 안 받는다) 깨지면 안 된다.
		// NON_NULL 은 앱 전역 설정(application.yaml jackson.default-property-inclusion)과 맞춘 것 —
		// targetJson/valueJson 저장 모양이 docs/DOMAIN.md 예시(null 필드 생략)와 일치해야 한다.
		// WRITE_DATES_AS_TIMESTAMPS 를 끈다 - JavaTimeModule 기본값은 LocalDateTime 을
		// [2026,8,13,14,0,0] 같은 배열로 내보내는데, 파이썬 Pydantic 은 ISO-8601 문자열만 받는다
		// (docs/API_CONTRACT.md 3-4번). 이거 안 끄면 reference_datetime 있는 모든 요청이 422 로 튕긴다.
		return JsonMapper.builder()
				.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
				.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
				.serializationInclusion(JsonInclude.Include.NON_NULL)
				.build()
				.findAndRegisterModules();
	}

	@Bean
	public RestClient aiRestClient(
			@Value("${ai.base-url}") String aiBaseUrl,
			@Qualifier(AI_OBJECT_MAPPER) ObjectMapper aiObjectMapper) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
		requestFactory.setReadTimeout((int) Duration.ofSeconds(60).toMillis());

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
