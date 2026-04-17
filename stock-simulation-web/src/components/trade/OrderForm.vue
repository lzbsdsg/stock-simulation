<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useTradeStore } from '@/stores/trade'
import { formatPrice } from '@/utils/format'
import type { OrderType, TradeSide } from '@/types/trade'

const props = defineProps<{
  stockCode?: string
}>()

const emit = defineEmits<{
  placed: []
}>()

const tradeStore = useTradeStore()

const form = reactive({
  stockCode: props.stockCode ?? '',
  side: 'BUY' as TradeSide,
  orderType: 'LIMIT' as OrderType,
  price: 10,
  quantity: 100,
})

watch(
  () => props.stockCode,
  (value) => {
    if (typeof value === 'string') {
      form.stockCode = value
    }
  },
)

const canSubmit = computed(() => {
  return form.stockCode.trim().length > 0 && form.quantity > 0 && form.quantity % 100 === 0
})

const estimatedAmount = computed(() => {
  if (form.orderType !== 'LIMIT') {
    return null
  }
  return form.price * form.quantity
})

const sideHint = computed(() => {
  if (form.side === 'BUY') {
    return '买入将冻结可用资金，待撮合后结算。'
  }
  return '卖出前请确认可卖数量充足。'
})

function generateClientOrderId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

async function submitOrder(): Promise<void> {
  if (!canSubmit.value) {
    ElMessage.warning('请检查股票代码与委托数量（100股整数倍）')
    return
  }

  try {
    await tradeStore.place({
      clientOrderId: generateClientOrderId(),
      stockCode: form.stockCode.trim().toLowerCase(),
      side: form.side,
      orderType: form.orderType,
      price: form.orderType === 'LIMIT' ? form.price : null,
      quantity: form.quantity,
    })
    ElMessage.success('下单成功')
    emit('placed')
  } catch (error) {
    const message = error instanceof Error ? error.message : '下单失败'
    ElMessage.error(message)
  }
}
</script>

<template>
  <section class="trade-panel">
    <div class="section-card-head">
      <div>
        <h3>交易下单</h3>
        <p class="section-card-subtitle">下单后进入撮合流程，可在委托列表中撤单</p>
      </div>
      <span class="panel-tag">{{ form.side === 'BUY' ? '买入' : '卖出' }}</span>
    </div>

    <el-form label-width="84px" class="trade-form">
      <div class="trade-form-grid">
        <el-form-item label="股票代码">
          <el-input v-model="form.stockCode" placeholder="如 sh600519" />
        </el-form-item>

        <el-form-item label="买卖方向">
          <el-radio-group v-model="form.side">
            <el-radio-button label="BUY">买入</el-radio-button>
            <el-radio-button label="SELL">卖出</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="订单类型">
          <el-radio-group v-model="form.orderType">
            <el-radio-button label="LIMIT">限价</el-radio-button>
            <el-radio-button label="MARKET">市价</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="form.orderType === 'LIMIT'" label="委托价格">
          <el-input-number v-model="form.price" :min="0.01" :precision="2" :step="0.01" />
        </el-form-item>

        <el-form-item label="委托数量">
          <el-input-number v-model="form.quantity" :min="100" :step="100" />
        </el-form-item>
      </div>

      <div class="trade-intent-row">
        <span class="trade-intent-text">{{ sideHint }}</span>
        <span class="trade-intent-amount mono-number">
          {{ estimatedAmount === null ? '市价单金额按成交价计算' : `预计委托金额 ${formatPrice(estimatedAmount)}` }}
        </span>
      </div>

      <el-form-item>
        <el-button type="primary" :loading="tradeStore.placingOrder" @click="submitOrder">提交委托</el-button>
      </el-form-item>
    </el-form>
  </section>
</template>
