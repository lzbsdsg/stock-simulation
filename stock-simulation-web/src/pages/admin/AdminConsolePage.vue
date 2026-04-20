<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getDashboardStats } from '@/api/admin'
import { getRealtimeMetrics } from '@/api/market'
import type { AdminDashboardStats } from '@/types/admin'
import type { MarketLatencyMetric, MarketRealtimeMetrics } from '@/types/market'
import { formatPrice } from '@/utils/format'

const REFRESH_INTERVAL_MS = 3000
const REALTIME_PULL_INTERVAL_MS = 1000
const ACTIVE_WINDOW_MS = 8000
const ACTIVE_BATCH_SIZE = 800
const ROUND_ROBIN_BATCH_SIZE = 100
const QUOTE_CACHE_TTL_MS = 5000
const QUOTE_STALE_TTL_SECONDS = 300
const DB_MASTER_MAX_POOL = 20
const DB_REDIS_MAX_ACTIVE = 16
const WS_MAX_CONNECTIONS = 10000
const ASYNC_CORE_POOL_SIZE = 8
const ASYNC_MAX_POOL_SIZE = 32
const ASYNC_QUEUE_CAPACITY = 500
const RABBIT_MATCH_CONCURRENT = 8
const RABBIT_MATCH_PREFETCH = 10

const loading = ref(false)
const refreshing = ref(false)
const stats = ref<AdminDashboardStats | null>(null)
const metrics = ref<MarketRealtimeMetrics | null>(null)

let refreshTimer: number | null = null

const latencyRows = computed(() => {
  const data = metrics.value
  if (!data) {
    return []
  }
  return [
    data.ingestCycleLatency,
    data.pubSubFanoutLatency,
    data.wsQueueLatency,
    data.wsPushLatency,
  ].filter((item): item is MarketLatencyMetric => Boolean(item))
})

const sampledAtText = computed(() => {
  if (!metrics.value?.sampledAt) {
    return '--'
  }
  const date = new Date(metrics.value.sampledAt)
  if (Number.isNaN(date.getTime())) {
    return metrics.value.sampledAt
  }
  return date.toLocaleString('zh-CN', { hour12: false })
})

const quoteCacheHitRatio = computed(() => {
  const l1 = stats.value?.marketQuoteCacheHitL1Total ?? 0
  const l2 = stats.value?.marketQuoteCacheHitL2Total ?? 0
  const total = l1 + l2
  if (total <= 0) {
    return null
  }
  return (l1 / total) * 100
})

const realtimeStrategyRows = computed(() => [
  { label: '行情拉取周期', value: formatMs(REALTIME_PULL_INTERVAL_MS) },
  { label: '活跃股票窗口', value: formatMs(ACTIVE_WINDOW_MS) },
  { label: '活跃股票批量', value: formatNumber(ACTIVE_BATCH_SIZE) },
  { label: '轮巡股票批量', value: formatNumber(ROUND_ROBIN_BATCH_SIZE) },
  { label: '行情缓存 TTL', value: formatMs(QUOTE_CACHE_TTL_MS) },
  { label: '陈旧缓存保留', value: formatSeconds(QUOTE_STALE_TTL_SECONDS) },
])

const capacityRows = computed(() => [
  { label: 'WebSocket 最大连接', value: formatNumber(WS_MAX_CONNECTIONS), hint: '连接并发上限' },
  { label: '异步线程池 core/max', value: `${ASYNC_CORE_POOL_SIZE}/${ASYNC_MAX_POOL_SIZE}`, hint: 'CallerRunsPolicy' },
  { label: '异步队列容量', value: formatNumber(ASYNC_QUEUE_CAPACITY), hint: '任务排队上限' },
  { label: 'Rabbit 消费并发', value: `${RABBIT_MATCH_CONCURRENT}/${RABBIT_MATCH_CONCURRENT}`, hint: '并发/最大并发' },
  { label: 'Rabbit 预取数', value: formatNumber(RABBIT_MATCH_PREFETCH), hint: '单消费者预取' },
  { label: 'DB 主库连接池', value: formatNumber(DB_MASTER_MAX_POOL), hint: 'Hikari maxPoolSize' },
  { label: 'Redis 连接池活跃', value: formatNumber(DB_REDIS_MAX_ACTIVE), hint: 'Lettuce max-active' },
  { label: '行情限流 visible-codes', value: '120/min', hint: '约 2.0 QPS' },
  { label: '行情限流 realtime-metrics', value: '60/min', hint: '约 1.0 QPS' },
  { label: '交易限流 place/cancel', value: '10/min', hint: '约 0.17 QPS' },
  { label: '用户持仓/概览限流', value: '100/min', hint: '约 1.7 QPS' },
])

onMounted(async () => {
  await loadData(false)
  startAutoRefresh()
})

onBeforeUnmount(() => {
  stopAutoRefresh()
})

async function loadData(silent: boolean) {
  if (silent) {
    refreshing.value = true
  } else {
    loading.value = true
  }

  try {
    const [statsResult, metricsResult] = await Promise.allSettled([getDashboardStats(), getRealtimeMetrics()])

    if (statsResult.status === 'fulfilled') {
      stats.value = statsResult.value
    }

    if (metricsResult.status === 'fulfilled') {
      metrics.value = metricsResult.value
    }

    if (statsResult.status === 'rejected' && metricsResult.status === 'rejected' && !silent) {
      ElMessage.error('管理员面板加载失败，请稍后重试')
    }

    if (statsResult.status === 'rejected') {
      console.error('[admin] load dashboard stats failed', statsResult.reason)
    }

    if (metricsResult.status === 'rejected') {
      console.error('[admin] load realtime metrics failed', metricsResult.reason)
    }
  } catch (error) {
    if (!silent) {
      ElMessage.error('管理员面板加载失败，请稍后重试')
    }
    console.error('[admin] load dashboard failed', error)
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function startAutoRefresh() {
  stopAutoRefresh()
  refreshTimer = window.setInterval(() => {
    void loadData(true)
  }, REFRESH_INTERVAL_MS)
}

function stopAutoRefresh() {
  if (refreshTimer !== null) {
    window.clearInterval(refreshTimer)
    refreshTimer = null
  }
}

function formatNumber(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '--'
  }
  return new Intl.NumberFormat('zh-CN').format(value)
}

function formatMs(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '--'
  }
  return `${value.toFixed(2)} ms`
}

function formatSeconds(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '--'
  }
  return value >= 60 ? `${(value / 60).toFixed(1)} min` : `${value.toFixed(0)} s`
}

function formatPercent(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '--'
  }
  return `${value.toFixed(2)}%`
}

function formatLatencyValue(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '--'
  }
  return value.toFixed(2)
}
</script>

<template>
  <section v-loading="loading" class="admin-page">
    <header class="admin-head">
      <div>
        <h1>管理员控制台</h1>
        <p>仅管理员可访问，面向系统运营和实时链路观测。</p>
      </div>
      <div class="admin-actions">
        <span class="sample-time">采样时间：{{ sampledAtText }}</span>
        <el-button :loading="refreshing" plain @click="loadData(false)">立即刷新</el-button>
      </div>
    </header>

    <section class="admin-section">
      <h2>系统概览</h2>
      <div class="admin-grid">
        <article class="metric-card">
          <span class="metric-label">总用户数</span>
          <strong>{{ formatNumber(stats?.totalUsers) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">活跃用户</span>
          <strong>{{ formatNumber(stats?.activeUsers) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">禁用用户</span>
          <strong>{{ formatNumber(stats?.disabledUsers) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">管理员数量</span>
          <strong>{{ formatNumber(stats?.adminUsers) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">今日新增用户</span>
          <strong>{{ formatNumber(stats?.todayNewUsers) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">总成交笔数</span>
          <strong>{{ formatNumber(stats?.totalTradeCount) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">今日成交笔数</span>
          <strong>{{ formatNumber(stats?.todayTradeCount) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">总成交额</span>
          <strong>{{ formatPrice(stats?.totalTradeAmount) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">今日成交额</span>
          <strong>{{ formatPrice(stats?.todayTradeAmount) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">系统可用资金</span>
          <strong>{{ formatPrice(stats?.totalAvailableBalance) }}</strong>
        </article>
      </div>
    </section>

    <section class="admin-section">
      <h2>实时性能面板</h2>
      <div class="strategy-note">
        <span>当前策略：1 秒定时调度，优先更新最近窗口内的活跃代码，缓存命中后直接读本地，只有变更才推送。</span>
      </div>
      <div class="admin-grid">
        <article v-for="row in realtimeStrategyRows" :key="row.label" class="metric-card strategy-card">
          <span class="metric-label">{{ row.label }}</span>
          <strong>{{ row.value }}</strong>
        </article>
      </div>
      <div class="admin-grid">
        <article class="metric-card">
          <span class="metric-label">活跃代码数</span>
          <strong>{{ formatNumber(metrics?.activeCodeCount) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">最近抓取代码数</span>
          <strong>{{ formatNumber(metrics?.lastIngestCodeCount) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">最近推送行情数</span>
          <strong>{{ formatNumber(metrics?.lastPublishedQuoteCount) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">最近抓取耗时</span>
          <strong>{{ formatMs(metrics?.lastIngestDurationMs) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">WebSocket连接数</span>
          <strong>{{ formatNumber(metrics?.wsActiveConnections) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">WebSocket队列任务</span>
          <strong>{{ formatNumber(metrics?.wsQueuedTasks) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">降级模式</span>
          <strong :class="metrics?.wsDegradedMode ? 'down' : 'up'">
            {{ metrics?.wsDegradedMode ? '已启用' : '正常' }}
          </strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">累计丢弃推送</span>
          <strong>{{ formatNumber(metrics?.wsDroppedTotal) }}</strong>
        </article>
      </div>

      <div class="latency-table-wrap">
        <el-table :data="latencyRows" stripe>
          <el-table-column prop="metric" label="指标" min-width="220" />
          <el-table-column prop="count" label="样本数" width="100" />
          <el-table-column label="均值(ms)" width="120">
            <template #default="scope">
              {{ formatLatencyValue(scope.row.meanMs) }}
            </template>
          </el-table-column>
          <el-table-column label="最大(ms)" width="120">
            <template #default="scope">
              {{ formatLatencyValue(scope.row.maxMs) }}
            </template>
          </el-table-column>
          <el-table-column label="P95(ms)" width="120">
            <template #default="scope">
              {{ formatLatencyValue(scope.row.p95Ms) }}
            </template>
          </el-table-column>
          <el-table-column label="P99(ms)" width="120">
            <template #default="scope">
              {{ formatLatencyValue(scope.row.p99Ms) }}
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section class="admin-section">
      <h2>容量与并发上限</h2>
      <div class="strategy-note">
        <span>这里展示的是当前项目的并发配置和接口限流，属于“可承载能力参考值”，不是线上实测 QPS。</span>
      </div>
      <div class="admin-grid">
        <article v-for="row in capacityRows" :key="row.label" class="metric-card strategy-card">
          <span class="metric-label">{{ row.label }}</span>
          <strong>{{ row.value }}</strong>
          <small class="capacity-hint">{{ row.hint }}</small>
        </article>
      </div>
    </section>

    <section class="admin-section">
      <h2>可观测指标总览</h2>
      <div class="admin-grid">
        <article class="metric-card">
          <span class="metric-label">累计下单数(持久化)</span>
          <strong>{{ formatNumber(stats?.tradeOrderCreatedTotal) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">累计成交数(持久化)</span>
          <strong>{{ formatNumber(stats?.tradeOrderFilledTotal) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">撮合耗时 P95</span>
          <strong>{{ formatMs(stats?.tradeMatchDurationP95Ms) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">撮合耗时 P99</span>
          <strong>{{ formatMs(stats?.tradeMatchDurationP99Ms) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">行情缓存命中 L1</span>
          <strong>{{ formatNumber(stats?.marketQuoteCacheHitL1Total) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">行情缓存命中 L2</span>
          <strong>{{ formatNumber(stats?.marketQuoteCacheHitL2Total) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">L1 命中占比</span>
          <strong>{{ formatPercent(quoteCacheHitRatio) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">WS 活跃连接</span>
          <strong>{{ formatNumber(stats?.wsActiveConnections) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">WS 累计丢弃</span>
          <strong>{{ formatNumber(stats?.wsPushDroppedTotal) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">DB 主库活跃连接</span>
          <strong>{{ formatNumber(stats?.dbPoolMasterActiveConnections) }}</strong>
        </article>
        <article class="metric-card">
          <span class="metric-label">DB 从库活跃连接</span>
          <strong>{{ formatNumber(stats?.dbPoolSlaveActiveConnections) }}</strong>
        </article>
      </div>
    </section>
  </section>
</template>

<style scoped>
.admin-page {
  display: grid;
  gap: 16px;
}

.admin-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.admin-head h1 {
  margin: 0;
  font-size: 32px;
}

.admin-head p {
  margin: 10px 0 0;
  color: var(--text-secondary);
}

.admin-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sample-time {
  font-size: 13px;
  color: var(--text-secondary);
}

.admin-section {
  background: var(--bg-panel);
  border: 1px solid var(--line-default);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  padding: 14px;
  display: grid;
  gap: 12px;
}

.admin-section h2 {
  margin: 0;
  font-size: 20px;
}

.strategy-note {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
}

.strategy-card {
  background: linear-gradient(180deg, rgba(42, 111, 255, 0.08), rgba(42, 111, 255, 0.02));
}

.capacity-hint {
  color: var(--text-secondary);
  font-size: 12px;
}

.admin-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 12px;
}

.latency-table-wrap {
  overflow-x: auto;
}

@media (max-width: 768px) {
  .admin-head {
    flex-direction: column;
  }

  .admin-actions {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
