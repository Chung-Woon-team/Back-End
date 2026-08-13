package com.Chung_Woon.Chung_Woon.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 도메인 에러를 여기 한 곳에 모아둔다. 새 에러가 필요하면 상수만 추가하면 된다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// 공통
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "입력값이 올바르지 않습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "지원하지 않는 요청 방식입니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "C003", "요청한 리소스를 찾을 수 없습니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C999", "서버 오류가 발생했습니다."),

	// 인증/인가
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "로그인이 필요합니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "A002", "권한이 없습니다."),

	// AI 서비스 연동 (API_CONTRACT.md 2-B "파이썬이 죽어 있을 때")
	AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI001", "AI 서비스를 사용할 수 없습니다."),
	AI_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "AI002", "AI 서비스 응답이 올바르지 않습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
