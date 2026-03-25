import { createRouter, createWebHistory } from 'vue-router'
import { pinia } from '@/stores'
import { useAuthStore } from '@/stores/auth'
import DefaultLayout from '@/layouts/DefaultLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      meta: { guestOnly: true },
      component: () => import('@/pages/auth/LoginPage.vue'),
    },
    {
      path: '/register',
      name: 'register',
      meta: { guestOnly: true },
      component: () => import('@/pages/auth/RegisterPage.vue'),
    },
    {
      path: '/forgot-password',
      name: 'forgot-password',
      meta: { guestOnly: true },
      component: () => import('@/pages/auth/ForgotPasswordPage.vue'),
    },
    {
      path: '/',
      component: DefaultLayout,
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          redirect: '/dashboard',
        },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/pages/DashboardPage.vue'),
        },
        {
          path: 'market',
          name: 'market',
          component: () => import('@/pages/market/MarketPage.vue'),
        },
        {
          path: 'market/:stockCode',
          name: 'stock-detail',
          component: () => import('@/pages/market/StockDetailPage.vue'),
          props: true,
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/dashboard',
    },
  ],
})

router.beforeEach((to) => {
  const authStore = useAuthStore(pinia)
  authStore.ensureInitialized()

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (to.meta.guestOnly && authStore.isAuthenticated) {
    return { path: '/dashboard' }
  }

  return true
})

export default router
