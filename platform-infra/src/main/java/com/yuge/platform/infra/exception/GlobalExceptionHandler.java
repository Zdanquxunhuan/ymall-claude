package com.yuge.platform.infra.exception;

import com.yuge.platform.infra.common.ErrorCode;
import com.yuge.platform.infra.common.Result;
import com.yuge.platform.infra.trace.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理所有Controller层抛出的异常，返回标准Result格式
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBizException(BizException e, HttpServletRequest request) {
        log.warn("[BizException] traceId={}, uri={}, code={}, message={}",
                TraceContext.getTraceId(), request.getRequestURI(), e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常 - @Valid @RequestBody
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("[ParamValidation] traceId={}, uri={}, message={}",
                TraceContext.getTraceId(), request.getRequestURI(), message);
        return Result.fail(ErrorCode.PARAM_ERROR, message);
    }

    /**
     * 处理参数校验异常 - @Valid @ModelAttribute
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e, HttpServletRequest request) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[BindException] traceId={}, uri={}, message={}",
                TraceContext.getTraceId(), request.getRequestURI(), message);
        return Result.fail(ErrorCode.PARAM_ERROR, message);
    }

    /**
     * 处理参数校验异常 - @Validated 方法参数
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e, HttpServletRequest request) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("[ConstraintViolation] traceId={}, uri={}, message={}",
                TraceContext.getTraceId(), request.getRequestURI(), message);
        return Result.fail(ErrorCode.PARAM_ERROR, message);
    }

    /**
     * 处理缺少请求参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException e, HttpServletRequest request) {
        String message = "缺少必填参数: " + e.getParameterName();
        log.warn("[MissingParam] traceId={}, uri={}, message={}",
                TraceContext.getTraceId(), request.getRequestURI(), message);
        return Result.fail(ErrorCode.PARAM_MISSING, message);
    }

    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String message = "参数类型错误: " + e.getName();
        log.warn("[TypeMismatch] traceId={}, uri={}, message={}",
                TraceContext.getTraceId(), request.getRequestURI(), message);
        return Result.fail(ErrorCode.PARAM_INVALID, message);
    }

    /**
     * 处理请求体解析异常
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e, HttpServletRequest request) {
        log.warn("[MessageNotReadable] traceId={}, uri={}, message={}",
                TraceContext.getTraceId(), request.getRequestURI(), e.getMessage());
        return Result.fail(ErrorCode.PARAM_ERROR, "请求体格式错误");
    }

    /**
     * 处理请求方法不支持异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.warn("[MethodNotSupported] traceId={}, uri={}, method={}",
                TraceContext.getTraceId(), request.getRequestURI(), e.getMethod());
        return Result.fail(ErrorCode.USER_ERROR, "不支持的请求方法: " + e.getMethod());
    }

    /**
     * 处理媒体类型不支持异常
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public Result<Void> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        log.warn("[MediaTypeNotSupported] traceId={}, uri={}, contentType={}",
                TraceContext.getTraceId(), request.getRequestURI(), e.getContentType());
        return Result.fail(ErrorCode.USER_ERROR, "不支持的媒体类型");
    }

    /**
     * 处理404异常
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoHandlerFoundException(NoHandlerFoundException e, HttpServletRequest request) {
        log.warn("[NotFound] traceId={}, uri={}",
                TraceContext.getTraceId(), request.getRequestURI());
        return Result.fail(ErrorCode.RESOURCE_NOT_FOUND, "接口不存在");
    }

    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("[SystemError] traceId={}, uri={}, message={}",
                TraceContext.getTraceId(), request.getRequestURI(), e.getMessage(), e);
        return Result.fail(ErrorCode.SYSTEM_ERROR, "系统内部错误，请稍后重试");
    }
}
