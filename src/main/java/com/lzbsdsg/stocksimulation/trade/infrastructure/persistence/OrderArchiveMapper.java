package com.lzbsdsg.stocksimulation.trade.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 委托订单归档与历史查询 Mapper。 */
@Mapper
public interface OrderArchiveMapper {

  @Select(
      """
      <script>
      SELECT
          id,
          user_id,
          client_order_id,
          stock_code,
          stock_name,
          side,
          order_type,
          status,
          price,
          quantity,
          filled_quantity,
          filled_amount,
          commission,
          frozen_amount,
          version,
          created_at,
          updated_at
      FROM (
          SELECT
              id,
              user_id,
              client_order_id,
              stock_code,
              stock_name,
              side,
              order_type,
              status,
              price,
              quantity,
              filled_quantity,
              filled_amount,
              commission,
              frozen_amount,
              version,
              created_at,
              updated_at
          FROM t_trade_order
          WHERE user_id = #{userId}
            AND created_at BETWEEN #{from} AND #{to}
          UNION ALL
          SELECT
              order_id AS id,
              user_id,
              client_order_id,
              stock_code,
              stock_name,
              side,
              order_type,
              status,
              price,
              quantity,
              filled_quantity,
              filled_amount,
              commission,
              frozen_amount,
              version,
              created_at,
              updated_at
          FROM t_trade_order_archive
          WHERE user_id = #{userId}
            AND created_at BETWEEN #{from} AND #{to}
      ) AS merged_orders
      ORDER BY created_at DESC, id DESC
      LIMIT #{size} OFFSET #{offset}
      </script>
      """)
  List<OrderDO> selectHistoryByUserIdAndCreatedAtBetween(
      @Param("userId") Long userId,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to,
      @Param("size") int size,
      @Param("offset") long offset);

  @Select(
      """
      <script>
      SELECT
          (
              SELECT COUNT(1)
              FROM t_trade_order
              WHERE user_id = #{userId}
                AND created_at BETWEEN #{from} AND #{to}
          ) +
          (
              SELECT COUNT(1)
              FROM t_trade_order_archive
              WHERE user_id = #{userId}
                AND created_at BETWEEN #{from} AND #{to}
          )
      </script>
      """)
  long countHistoryByUserIdAndCreatedAtBetween(
      @Param("userId") Long userId,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to);

  @Update(
      """
      <script>
      WITH candidate AS (
          SELECT
              o.id,
              o.user_id,
              o.client_order_id,
              o.stock_code,
              o.stock_name,
              o.side,
              o.order_type,
              o.status,
              o.price,
              o.quantity,
              o.filled_quantity,
              o.filled_amount,
              o.commission,
              o.frozen_amount,
              o.version,
              o.created_at,
              o.updated_at
          FROM t_trade_order o
          WHERE o.status IN ('CANCELLED', 'EXPIRED', 'REJECTED')
            AND o.updated_at &lt; #{cutoff}
            AND NOT EXISTS (
                SELECT 1
                FROM t_trade_record r
                WHERE r.order_id = o.id
            )
          ORDER BY o.updated_at ASC, o.id ASC
          LIMIT #{batchSize}
      ),
      inserted AS (
          INSERT INTO t_trade_order_archive (
              order_id,
              user_id,
              client_order_id,
              stock_code,
              stock_name,
              side,
              order_type,
              status,
              price,
              quantity,
              filled_quantity,
              filled_amount,
              commission,
              frozen_amount,
              version,
              created_at,
              updated_at,
              archived_at
          )
          SELECT
              c.id,
              c.user_id,
              c.client_order_id,
              c.stock_code,
              c.stock_name,
              c.side,
              c.order_type,
              c.status,
              c.price,
              c.quantity,
              c.filled_quantity,
              c.filled_amount,
              c.commission,
              c.frozen_amount,
              c.version,
              c.created_at,
              c.updated_at,
              NOW()
          FROM candidate c
          ON CONFLICT (order_id) DO NOTHING
          RETURNING order_id
      )
      DELETE FROM t_trade_order o
      WHERE o.id IN (SELECT order_id FROM inserted)
      </script>
      """)
  int archiveClosedOrdersWithoutTrades(
      @Param("cutoff") OffsetDateTime cutoff, @Param("batchSize") int batchSize);
}
