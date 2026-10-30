import { createRouter, createWebHistory } from 'vue-router'

const TOKEN_KEY = 'trade_token'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/RegisterView.vue'),
    meta: { public: true, title: '注册' }
  },
  {
    path: '/',
    name: 'products',
    component: () => import('../views/ProductListView.vue'),
    meta: { title: '商品' }
  },
  {
    path: '/products/:id',
    name: 'product-detail',
    component: () => import('../views/ProductDetailView.vue'),
    meta: { title: '商品详情' }
  },
  {
    path: '/checkout',
    name: 'checkout',
    component: () => import('../views/CheckoutView.vue'),
    meta: { title: '确认下单' }
  },
  {
    path: '/orders',
    name: 'orders',
    component: () => import('../views/OrderListView.vue'),
    meta: { title: '我的订单' }
  },
  {
    path: '/orders/:id',
    name: 'order-detail',
    component: () => import('../views/OrderDetailView.vue'),
    meta: { title: '订单详情' }
  },
  {
    path: '/points',
    name: 'points',
    component: () => import('../views/PointView.vue'),
    meta: { title: '我的积分' }
  },
  {
    path: '/account',
    name: 'account',
    component: () => import('../views/AccountView.vue'),
    meta: { title: '账户设置' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem(TOKEN_KEY)
  document.title = to.meta.title ? `${to.meta.title} · 积分交易中心` : '积分交易中心'

  if (!to.meta.public && !token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.public && token) {
    return { name: 'products' }
  }
  return true
})

export default router
