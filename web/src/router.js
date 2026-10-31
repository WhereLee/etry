import { createRouter, createWebHistory } from 'vue-router'
import { hasRole, isLoggedIn, session, setUnauthorizedHandler } from './store/session'

const routes = [
  { path: '/', name: 'courses', component: () => import('./views/CourseList.vue') },
  { path: '/courses/:id', name: 'course', component: () => import('./views/CourseDetail.vue') },
  {
    path: '/courses/:id/learn',
    name: 'learn',
    component: () => import('./views/Learn.vue'),
    meta: { auth: true }
  },
  { path: '/login', name: 'login', component: () => import('./views/Login.vue') },
  {
    path: '/password',
    name: 'password',
    component: () => import('./views/ChangePassword.vue'),
    meta: { auth: true, allowWhenMustChange: true }
  },
  {
    path: '/me/learning',
    name: 'myLearning',
    component: () => import('./views/MyLearning.vue'),
    meta: { auth: true }
  },
  {
    path: '/me/favorites',
    name: 'favorites',
    component: () => import('./views/Favorites.vue'),
    meta: { auth: true }
  },
  {
    path: '/teacher/courses',
    name: 'teacherCourses',
    component: () => import('./views/TeacherCourses.vue'),
    meta: { auth: true, role: 'TEACHER' }
  },
  {
    path: '/teacher/courses/:id',
    name: 'teacherCourseEdit',
    component: () => import('./views/TeacherCourseEdit.vue'),
    meta: { auth: true, role: 'TEACHER' }
  },
  {
    path: '/teacher/dashboard',
    name: 'teacherDashboard',
    component: () => import('./views/TeacherDashboard.vue'),
    meta: { auth: true, role: 'TEACHER' }
  },
  {
    path: '/admin/review',
    name: 'adminReview',
    component: () => import('./views/AdminReview.vue'),
    meta: { auth: true, role: 'ADMIN' }
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.meta.auth && !isLoggedIn()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  // 首次登录被要求改密时，除了改密页哪儿都去不了
  if (isLoggedIn() && session.mustChangePassword && !to.meta.allowWhenMustChange) {
    return { name: 'password', query: { forced: '1' } }
  }
  if (to.meta.role && !hasRole(to.meta.role)) {
    return { name: 'courses' }
  }
  return true
})

// token 失效时统一回登录页，并记住原来要去哪
setUnauthorizedHandler(() => {
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    router.push({ name: 'login', query: { redirect: current.fullPath } })
  }
})

export default router
