package com.lzbsdsg.stocksimulation.common.annotation;

import java.lang.annotation.*;

/**
 * 标记方法走从库（读写分离路由注解）。 标注在 Application Service 的查询方法上，DataSourceAspect 拦截后 通过 DataSourceContextHolder
 * 将当前线程路由到从库数据源。
 *
 * <p>使用场景：委托列表、成交记录、资金流水、收益曲线、管理后台查询等只读操作。 注意：写后立即读场景不要使用此注解，应强制走主库。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ReadOnly {}
