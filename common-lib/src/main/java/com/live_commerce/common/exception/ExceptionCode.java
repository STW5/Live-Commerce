package com.live_commerce.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 모든 서비스에서 사용하는 예외 코드 인터페이스
 */
public interface ExceptionCode {
    HttpStatus getHttpStatus();
    String getMessage();
}
