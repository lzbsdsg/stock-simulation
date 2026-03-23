package com.lzbsdsg.stocksimulation.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JSON 工具类 */
public final class JsonUtil {

  private static final Logger log = LoggerFactory.getLogger(JsonUtil.class);

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private JsonUtil() {}

  public static String toJson(Object obj) {
    try {
      return MAPPER.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      log.error("JSON序列化失败", e);
      throw new RuntimeException("JSON序列化失败", e);
    }
  }

  public static <T> T fromJson(String json, Class<T> clazz) {
    try {
      return MAPPER.readValue(json, clazz);
    } catch (JsonProcessingException e) {
      log.error("JSON反序列化失败", e);
      throw new RuntimeException("JSON反序列化失败", e);
    }
  }

  public static ObjectMapper getMapper() {
    return MAPPER;
  }
}
