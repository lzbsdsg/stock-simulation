package com.lzbsdsg.stocksimulation.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.Instant;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/** MyBatis-Plus 自动填充处理器（创建/更新时间）。 */
@Component
public class MybatisMetaObjectHandler implements MetaObjectHandler {

  @Override
  public void insertFill(MetaObject metaObject) {
    Instant now = Instant.now();
    this.strictInsertFill(metaObject, "createdAt", Instant.class, now);
    this.strictInsertFill(metaObject, "updatedAt", Instant.class, now);
  }

  @Override
  public void updateFill(MetaObject metaObject) {
    this.strictUpdateFill(metaObject, "updatedAt", Instant.class, Instant.now());
  }
}
