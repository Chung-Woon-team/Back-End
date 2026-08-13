package com.Chung_Woon.Chung_Woon.global.error;

import lombok.Getter;

/**
 * 서비스 로직에서 던지는 예외. GlobalExceptionHandler 가 받아 HTTP 응답으로 바꿔준다.
 * 사용법: {@code throw new BusinessException(ErrorCode.NOT_FOUND);}
 */
@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public BusinessException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}
}
