package com.Chung_Woon.Chung_Woon.global.common;

import java.util.List;

/**
 * 모든 API 가 공통으로 쓰는 응답 껍데기.
 * 성공: {"success": true, "data": {...}}
 * 실패: {"success": false, "error": {"code": "...", "message": "...", "fields": [...]}}
 */
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, data, null);
	}

	public static ApiResponse<Void> ok() {
		return new ApiResponse<>(true, null, null);
	}

	public static ApiResponse<Void> fail(String code, String message) {
		return new ApiResponse<>(false, null, new ErrorBody(code, message, null));
	}

	public static ApiResponse<Void> fail(String code, String message, List<FieldError> fields) {
		return new ApiResponse<>(false, null, new ErrorBody(code, message, fields));
	}

	public record ErrorBody(String code, String message, List<FieldError> fields) {
	}

	public record FieldError(String field, String message) {
	}
}
