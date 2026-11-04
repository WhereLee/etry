import { createRouter, createWebHistory } from 'vue-router'
import { hasRole, homePath, isLoggedIn, session } from './store/session'

const routes = [
  { path: '/', redirect: '/learning' },
  {
    path: '/login',
    name: 'login',
    component: () => import('./views/Login.vue')
  },
  {
    path: '/invite/:token',
    name: 'teacherInvite',
    component: () => import('./views/TeacherInvite.vue')
  },
  {
    path: '/password',
    name: 'password',
    component: () => import('./views/ChangePassword.vue'),
    meta: { auth: true, allowWhenMustChange: true }
  },
  {
    path: '/learning',
    name: 'learning',
    component: () => import('./views/LearningSpace.vue'),
    meta: { auth: true, role: 'STUDENT' }
  },
  {
    path: '/store',
    name: 'courseStore',
    component: () => import('./views/CourseStore.vue'),
    meta: { auth: true, role: 'STUDENT' }
  },
  {
    path: '/orders',
    name: 'myOrders',
    component: () => import('./views/MyOrders.vue'),
    meta: { auth: true, role: 'STUDENT' }
  },
  {
    path: '/orders/:orderNo',
    name: 'orderDetail',
    component: () => import('./views/OrderDetail.vue'),
    meta: { auth: true, role: 'STUDENT' }
  },
  {
    path: '/mock-pay/:orderNo',
    name: 'mockCashier',
    component: () => import('./views/MockCashier.vue'),
    meta: { auth: true, role: 'STUDENT' }
  },
  {
    path: '/series/:id',
    name: 'series',
    component: () => import('./views/SeriesView.vue'),
    meta: { auth: true, role: 'STUDENT' }
  },
  {
    path: '/admin/users',
    name: 'adminUsers',
    component: () => import('./views/AdminUsers.vue'),
    meta: { auth: true, role: 'ADMIN' }
  },
  {
    path: '/admin/teacher-invitations',
    name: 'adminTeacherInvitations',
    component: () => import('./views/AdminTeacherInvitations.vue'),
    meta: { auth: true, role: 'ADMIN' }
  },
  {
    path: '/admin/series',
    name: 'adminSeries',
    component: () => import('./views/AdminSeries.vue'),
    meta: { auth: true, role: 'ADMIN' }
  },
  {
    path: '/admin/orders',
    name: 'adminOrders',
    component: () => import('./views/AdminOrders.vue'),
    meta: { auth: true, role: 'ADMIN' }
  },
  {
    path: '/teacher/series',
    name: 'teacherSeries',
    component: () => import('./views/TeacherSeries.vue'),
    meta: { auth: true, role: 'TEACHER' }
  },
  {
    path: '/teacher/series/new',
    name: 'teacherSeriesNew',
    component: () => import('./views/TeacherSeriesEditor.vue'),
    meta: { auth: true, role: 'TEACHER' }
  },
  {
    path: '/teacher/series/:id',
    name: 'teacherSeriesDetail',
    component: () => import('./views/TeacherSeriesView.vue'),
    meta: { auth: true, role: 'TEACHER' }
  },
  {
    path: '/teacher/series/:id/edit',
    name: 'teacherSeriesEdit',
    component: () => import('./views/TeacherSeriesEditor.vue'),
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
