package com.Chung_Woon.Chung_Woon.global.error;

import com.Chung_Woon.Chung_Woon.global.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
		ErrorCode ec = e.getErrorCode();
		log.warn("BusinessException: {} - {}", ec.getCode(), e.getMessage());
		return ResponseEntity.status(ec.getStatus())
				.body(ApiResponse.fail(ec.getCode(), e.getMessage()));
	}

	/** @Valid 검증 실패 - 어떤 필드가 왜 틀렸는지 그대로 내려준다. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
		List<ApiResponse.FieldError> fields = e.getBindingResult().getFieldErrors().stream()
				.map(fe -> new ApiResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
				.toList();
		ErrorCode ec = ErrorCode.INVALID_INPUT;
		return ResponseEntity.status(ec.getStatus())
				.body(ApiResponse.fail(ec.getCode(), ec.getMessage(), fields));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
		ErrorCode ec = ErrorCode.NOT_FOUND;
		return ResponseEntity.status(ec.getStatus())
				.body(ApiResponse.fail(ec.getCode(), ec.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
		log.error("Unhandled exception", e);
		ErrorCode ec = ErrorCode.INTERNAL_ERROR;
		return ResponseEntity.status(ec.getStatus())
				.body(ApiResponse.fail(ec.getCode(), ec.getMessage()));
	}
}
