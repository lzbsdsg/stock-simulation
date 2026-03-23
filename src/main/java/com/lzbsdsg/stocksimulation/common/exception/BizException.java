package com.lzbsdsg.stocksimulation.common.exception;

import com.lzbsdsg.stocksimulation.common.result.ErrorCode;

/**
 * 业务异常
 *
 * <p>所有可预期的业务错误均抛出此异常，由全局异常处理器统一捕获。
 */
public class BizException extends RuntimeException {

  private final ErrorCode errorCode;

  public BizException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  public BizException(ErrorCode errorCode, String detail) {
    super(detail);
    this.errorCode = errorCode;
  }

  public BizException(ErrorCode errorCode, Throwable cause) {
    super(errorCode.getMessage(), cause);
    this.errorCode = errorCode;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
