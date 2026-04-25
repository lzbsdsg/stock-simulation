package com.lzbsdsg.stocksimulation.portfolio.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.lzbsdsg.stocksimulation.portfolio.domain.entity.Position;
import com.lzbsdsg.stocksimulation.portfolio.infrastructure.persistence.PositionDO;
import com.lzbsdsg.stocksimulation.portfolio.infrastructure.persistence.PositionMapper;
import com.lzbsdsg.stocksimulation.portfolio.infrastructure.persistence.PositionRepositoryImpl;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;

/** Position Repository 测试。 */
class PositionRepositoryIntegrationTest {

  private PositionMapper positionMapper;
  private PositionRepositoryImpl positionRepository;

  @BeforeEach
  void setUp() {
    positionMapper = Mockito.mock(PositionMapper.class);
    positionRepository = new PositionRepositoryImpl(positionMapper);
  }

  @Test
  void should_save_and_query_position_by_user_and_stock() {
    when(positionMapper.insert(any(PositionDO.class)))
        .thenAnswer(
            invocation -> {
              PositionDO inserted = invocation.getArgument(0);
              inserted.setId(1L);
              return 1;
            });

    Position position = new Position();
    position.setUserId(1001L);
    position.setStockCode("sh600519");
    position.setStockName("贵州茅台");
    position.setTotalQuantity(100);
    position.setAvailableQuantity(0);
    position.setFrozenQuantity(100);
    position.setCostPrice(new BigDecimal("10.0000"));
    position.setTotalCost(new BigDecimal("1000.00"));
    position.setVersion(0);
    positionRepository.save(position);
    assertEquals(1L, position.getId());

    PositionDO stored = new PositionDO();
    stored.setId(1L);
    stored.setUserId(1001L);
    stored.setStockCode("sh600519");
    stored.setStockName("贵州茅台");
    stored.setTotalQuantity(100);
    stored.setAvailableQuantity(0);
    stored.setFrozenQuantity(100);
    stored.setCostPrice(new BigDecimal("10.0000"));
    stored.setTotalCost(new BigDecimal("1000.00"));
    stored.setVersion(0);
    when(positionMapper.selectOne(any())).thenReturn(stored);

    Optional<Position> queried = positionRepository.findByUserIdAndStockCode(1001L, "sh600519");
    assertTrue(queried.isPresent());
    assertEquals(100, queried.get().getTotalQuantity());
    assertEquals(new BigDecimal("10.0000"), queried.get().getCostPrice());
  }

  @Test
  void should_enforce_unique_user_stock_constraint() {
    when(positionMapper.insert(any(PositionDO.class)))
        .thenThrow(new DuplicateKeyException("duplicate key value violates unique constraint"));

    Position duplicate = new Position();
    duplicate.setUserId(1001L);
    duplicate.setStockCode("sh600519");
    duplicate.setStockName("贵州茅台");
    duplicate.setTotalQuantity(100);
    duplicate.setAvailableQuantity(0);
    duplicate.setFrozenQuantity(100);
    duplicate.setCostPrice(new BigDecimal("10.0000"));
    duplicate.setTotalCost(new BigDecimal("1000.00"));
    duplicate.setVersion(0);

    assertThrows(DuplicateKeyException.class, () -> positionRepository.save(duplicate));
  }

  @Test
  void should_update_position_with_optimistic_lock() {
    Position position = new Position();
    position.setId(1L);
    position.setUserId(1001L);
    position.setStockCode("sh600519");
    position.setVersion(3);
    position.setTotalQuantity(100);
    position.setAvailableQuantity(0);
    position.setFrozenQuantity(100);
    position.setCostPrice(new BigDecimal("10.0000"));
    position.setTotalCost(new BigDecimal("1000.00"));

    when(positionMapper.updateById(any(PositionDO.class))).thenReturn(1);
    assertTrue(positionRepository.updateWithVersion(position));

    when(positionMapper.updateById(any(PositionDO.class))).thenReturn(0);
    assertFalse(positionRepository.updateWithVersion(position));
  }
}
