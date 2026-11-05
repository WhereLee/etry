import { createRouter, createWebHistory } from 'vue-router'
import { hasRole, homePath, isLoggedIn, session } from '@/store/session'

const routes = [
  { path: '/', redirect: '/learning' },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/features/identity/views/Login.vue')
  },
  {
    path: '/invite/:token',
    name: 'teacherInvite',
    component: () => import('@/features/identity/views/TeacherInvite.vue')
  },
  {
    path: '/password',
    name: 'password',
    component: () => import('@/features/identity/views/ChangePassword.vue'),
    meta: { auth: true, allowWhenMustChange: true }
  },
  {
    path: '/learning',
    name: 'learning',
    component: () => import('@/features/learning/views/LearningSpace.vue'),
    meta: { auth: true, role: 'STUDENT' }
  },
  {
    path: '/store',
    name: 'courseStore',
    component: () => import('@/features/commerce/views/CourseStore.vue'),
    meta: { auth: true, role: 'STUDENT' }
  },
  {
    path: '/orders',
    name: 'myOrders',
    component: () => import('@/features/commerce/views/MyOrders.vue'),
    meta: { auth: true, role: 'STUDENT' }
  },
  {
    path: '/orders/:orderNo',
    name: 'orderDetail',
    component: () => import('@/features/commerce/views/OrderDetail.vue'),
    meta: { auth: true, role: 'STUDENT' }
  },
  {
    path: '/mock-pay/:orderNo',
    name: 'mockCashier',
    component: () => import('@/features/commerce/views/MockCashier.vue'),
    meta: { auth: true, role: 'STUDENT' }
  },
  {
    path: '/series/:id',
    name: 'series',
    component: () => import('@/features/learning/views/SeriesView.vue'),
    meta: { auth: true, role: 'STUDENT' }
  },
  {
    path: '/admin/users',
    name: 'adminUsers',
    component: () => import('@/features/operations/views/AdminUsers.vue'),
    meta: { auth: true, role: 'ADMIN' }
  },
  {
    path: '/admin/teacher-invitations',
    name: 'adminTeacherInvitations',
    component: () => import('@/features/operations/views/AdminTeacherInvitations.vue'),
    meta: { auth: true, role: 'ADMIN' }
  },
  {
    path: '/admin/series',
    name: 'adminSeries',
    component: () => import('@/features/operations/views/AdminSeries.vue'),
    meta: { auth: true, role: 'ADMIN' }
  },
  {
    path: '/admin/orders',
    name: 'adminOrders',
    component: () => import('@/features/operations/views/AdminOrders.vue'),
    meta: { auth: true, role: 'ADMIN' }
  },
  {
    path: '/teacher/series',
    name: 'teacherSeries',
    component: () => import('@/features/course/views/TeacherSeries.vue'),
    meta: { auth: true, role: 'TEACHER' }
  },
  {
    path: '/teacher/series/new',
    name: 'teacherSeriesNew',
    component: () => import('@/features/course/views/TeacherSeriesEditor.vue'),
    meta: { auth: true, role: 'TEACHER' }
  },
  {
    path: '/teacher/series/:id',
    name: 'teacherSeriesDetail',
    component: () => import('@/features/course/views/TeacherSeriesView.vue'),
    meta: { auth: true, role: 'TEACHER' }
  },
  {
    path: '/teacher/series/:id/edit',
    name: 'teacherSeriesEdit',
    component: () => import('@/features/course/views/TeacherSeriesEditor.vue'),
    meta: { auth: true, role: 'TEACHER' }
  },
  { path: '/:pathMatch(.*)*', redirect: '/learning' }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

router.beforeEach((to) => {
  if (to.meta.auth && !isLoggedIn()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (isLoggedIn() && session.mustChangePassword && !to.meta.allowWhenMustChange) {
    return { name: 'password', query: { forced: '1' } }
  }
  if (to.meta.role && !hasRole(to.meta.role)) {
    return homePath()
  }
  if (to.name === 'login' && isLoggedIn()) {
    return homePath()
  }
  return true
})

export default router
