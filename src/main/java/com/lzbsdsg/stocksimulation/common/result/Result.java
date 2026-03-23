package com.lzbsdsg.stocksimulation.common.result;

import java.time.Instant;

/**
 * 统一响应封装
 *
 * @param <T> 数据类型
 */
public class Result<T> {

  private int code;
  private String message;
  private T data;
  private String traceId;
  private Instant timestamp;

  public Result() {
    this.timestamp = Instant.now();
  }

  public static <T> Result<T> success(T data) {
    Result<T> result = new Result<>();
    result.code = 200;
    result.message = "success";
    result.data = data;
    return result;
  }

  public static <T> Result<T> success() {
    return success(null);
  }

  public static <T> Result<T> fail(int code, String message) {
    Result<T> result = new Result<>();
    result.code = code;
    result.message = message;
    return result;
  }

  public static <T> Result<T> fail(ErrorCode errorCode) {
    Result<T> result = new Result<>();
    result.code = errorCode.getCode();
    result.message = errorCode.getMessage();
    return result;
  }

  // ==================== Getters & Setters ====================

  public int getCode() {
    return code;
  }

  public void setCode(int code) {
    this.code = code;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public T getData() {
    return data;
  }

  public void setData(T data) {
    this.data = data;
  }

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Instant timestamp) {
    this.timestamp = timestamp;
  }
}
