import { ref } from 'vue'
import { defineStore } from 'pinia'
import * as portfolioApi from '@/api/portfolio'
import type {
  EquityCurve,
  FundFlowItem,
  PortfolioOverview,
  PositionItem,
} from '@/types/portfolio'

export const usePortfolioStore = defineStore('portfolio', () => {
  const overview = ref<PortfolioOverview | null>(null)
  const positions = ref<PositionItem[]>([])
  const equityCurve = ref<EquityCurve | null>(null)
  const fundFlows = ref<FundFlowItem[]>([])
  const loading = ref(false)
  const positionsLoading = ref(false)
  const positionsPage = ref(1)
  const positionsSize = ref(20)
  const positionsTotal = ref(0)

  async function loadOverview(): Promise<void> {
    overview.value = await portfolioApi.getOverview()
  }

  async function loadPositions(page = positionsPage.value, size = positionsSize.value): Promise<void> {
    positionsLoading.value = true
    try {
      const pageResult = await portfolioApi.getPositions(page, size)
      positions.value = pageResult.records
      positionsTotal.value = pageResult.total
      positionsPage.value = pageResult.page
      positionsSize.value = pageResult.size
    } finally {
      positionsLoading.value = false
    }
  }

  async function loadEquityCurve(days = 30): Promise<void> {
    equityCurve.value = await portfolioApi.getEquityCurve(days)
  }

  async function loadFundFlows(page = 1, size = 20): Promise<void> {
    const pageResult = await portfolioApi.getFundFlows(page, size)
    fundFlows.value = pageResult.records
  }

  async function refreshAll(days = 30): Promise<void> {
    loading.value = true
    try {
      await Promise.all([
        loadOverview(),
        loadPositions(positionsPage.value, positionsSize.value),
        loadEquityCurve(days),
        loadFundFlows(),
      ])
    } finally {
      loading.value = false
    }
  }

  return {
    overview,
    positions,
    equityCurve,
    fundFlows,
    loading,
    positionsLoading,
    positionsPage,
    positionsSize,
    positionsTotal,
    loadOverview,
    loadPositions,
    loadEquityCurve,
    loadFundFlows,
    refreshAll,
  }
})
