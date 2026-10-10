package com.muqiu.managentcenter.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 统一异常处理，不向响应或运行日志泄漏请求正文。 */
@RestControllerAdvice
public class ApiExceptionHandler {
    /** 运行日志。 */
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** 映射业务失败，提供审计用的安全错误码。 */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> business(ApiException exception, HttpServletRequest request) {
        ErrorCode error = exception.error();
        request.setAttribute("audit.errorCode", error.code());
        log.warn("[业务处理] 操作失败 requestId={} code={}", request.getAttribute("audit.requestId"), error.code());
        return ResponseEntity.status(error.httpStatus()).body(ApiResponse.failure(error));
    }

    /** 映射格式和约束错误，不输出校验对象或原始值。 */
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            ConstraintViolationException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> invalid(Exception exception, HttpServletRequest request) {
        request.setAttribute("audit.errorCode", ErrorCode.INVALID_INPUT.code());
        return ResponseEntity.badRequest().body(ApiResponse.failure(ErrorCode.INVALID_INPUT));
    }

    /** 映射数据库唯一键竞争。 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> duplicate(DuplicateKeyException exception, HttpServletRequest request) {
        request.setAttribute("audit.errorCode", ErrorCode.DUPLICATE.code());
        return ResponseEntity.status(409).body(ApiResponse.failure(ErrorCode.DUPLICATE));
    }

    /** 映射锁竞争，禁止静默覆盖数据。 */
    @ExceptionHandler(CannotAcquireLockException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(CannotAcquireLockException exception, HttpServletRequest request) {
        request.setAttribute("audit.errorCode", ErrorCode.CONFLICT.code());
        return ResponseEntity.status(409).body(ApiResponse.failure(ErrorCode.CONFLICT));
    }

    /** 未知失败仅记录请求标识及异常类型，防止异常原文泄漏 SQL/凭据。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception exception, HttpServletRequest request) {
        request.setAttribute("audit.errorCode", ErrorCode.INTERNAL_ERROR.code());
        log.error("[业务处理] 内部失败 requestId={} exceptionType={}",
                request.getAttribute("audit.requestId"), exception.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR));
    }
}
