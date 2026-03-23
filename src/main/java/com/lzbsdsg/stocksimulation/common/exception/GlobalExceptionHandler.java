package com.lzbsdsg.stocksimulation.common.exception;

import com.lzbsdsg.stocksimulation.common.result.ErrorCode;
import com.lzbsdsg.stocksimulation.common.result.Result;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 全局异常处理器 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** 业务异常 */
  @ExceptionHandler(BizException.class)
  public ResponseEntity<Result<Void>> handleBizException(BizException ex) {
    log.warn("BizException: code={}, message={}", ex.getErrorCode().getCode(), ex.getMessage());
    Result<Void> result = Result.fail(ex.getErrorCode());
    return ResponseEntity.status(mapHttpStatus(ex.getErrorCode())).body(result);
  }

  /** 参数校验异常 */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Result<Void>> handleValidationException(
      MethodArgumentNotValidException ex) {
    String errorMsg =
        ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining("; "));
    log.warn("Validation failed: {}", errorMsg);
    Result<Void> result = Result.fail(ErrorCode.BAD_REQUEST.getCode(), errorMsg);
    return ResponseEntity.badRequest().body(result);
  }

  /** 数据唯一约束/主键冲突异常 */
  @ExceptionHandler({DuplicateKeyException.class, DataIntegrityViolationException.class})
  public ResponseEntity<Result<Void>> handleDataConflict(Exception ex) {
    log.warn("Data conflict: {}", ex.getMessage());
    Result<Void> result = Result.fail(ErrorCode.CONFLICT.getCode(), "数据冲突，请检查是否重复提交");
    return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
  }

  /** 静态资源未找到异常（例如 /v3/api-docs/ 尾斜杠） */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Result<Void>> handleNoResourceFoundException(NoResourceFoundException ex) {
    log.debug("NoResourceFoundException: {}", ex.getMessage());
    Result<Void> result =
        Result.fail(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
  }

  /** 路由未匹配异常 */
  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<Result<Void>> handleNoHandlerFoundException(NoHandlerFoundException ex) {
    log.debug("NoHandlerFoundException: {} {}", ex.getHttpMethod(), ex.getRequestURL());
    Result<Void> result =
        Result.fail(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
  }

  /** 请求头缺失异常（例如登出缺少 Authorization） */
  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<Result<Void>> handleMissingRequestHeaderException(
      MissingRequestHeaderException ex) {
    if ("Authorization".equalsIgnoreCase(ex.getHeaderName())) {
      log.warn("Missing Authorization header");
      Result<Void> result = Result.fail(ErrorCode.UNAUTHORIZED);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
    }
    log.warn("Missing required header: {}", ex.getHeaderName());
    Result<Void> result = Result.fail(ErrorCode.BAD_REQUEST.getCode(), ex.getMessage());
    return ResponseEntity.badRequest().body(result);
  }

  /** 未知异常 */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Result<Void>> handleException(Exception ex) {
    log.error("Unexpected exception", ex);
    Result<Void> result = Result.fail(ErrorCode.INTERNAL_ERROR);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
  }

  /** 根据错误码映射 HTTP 状态码 */
  private HttpStatus mapHttpStatus(ErrorCode errorCode) {
    return switch (errorCode) {
      case UNAUTHORIZED,
              AUTH_TOKEN_EXPIRED,
              AUTH_TOKEN_INVALID,
              AUTH_REFRESH_TOKEN_INVALID,
              AUTH_LOGIN_FAILED ->
          HttpStatus.UNAUTHORIZED;
      case FORBIDDEN, AUTH_ACCOUNT_LOCKED, TRADE_ORDER_NOT_OWN -> HttpStatus.FORBIDDEN;
      case NOT_FOUND,
              USER_NOT_FOUND,
              USER_ACCOUNT_NOT_FOUND,
              MARKET_STOCK_NOT_FOUND,
              TRADE_ORDER_NOT_FOUND,
              PORTFOLIO_POSITION_NOT_FOUND,
              NOTIFICATION_NOT_FOUND ->
          HttpStatus.NOT_FOUND;
      case CONFLICT,
              AUTH_EMAIL_ALREADY_REGISTERED,
              TRADE_ORDER_DUPLICATE,
              TRADE_ORDER_CANNOT_CANCEL,
              WATCHLIST_ALREADY_EXISTS ->
          HttpStatus.CONFLICT;
      case TOO_MANY_REQUESTS, AUTH_OTP_SEND_TOO_FREQUENT, AUTH_OTP_IP_LIMIT ->
          HttpStatus.TOO_MANY_REQUESTS;
      case MARKET_DATA_UNAVAILABLE, MARKET_PROVIDER_ALL_FAILED -> HttpStatus.SERVICE_UNAVAILABLE;
      default -> HttpStatus.BAD_REQUEST;
    };
  }
}
