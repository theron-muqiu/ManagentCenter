import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '../stores/auth'
import { getToken } from '../api/http'

export const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/login', component: () => import('../views/LoginView.vue') },
  { path: '/', component: () => import('../layouts/AdminLayout.vue'), children: [
    { path: '', component: () => import('../views/HomeView.vue') },
    { path: 'system/users', component: () => import('../views/UserView.vue'), meta: { permission: 'user:query' } },
    { path: 'system/roles', component: () => import('../views/RoleView.vue'), meta: { permission: 'role:query' } },
    { path: 'audit/operations', component: () => import('../views/OperationLogView.vue'), meta: { permission: 'operation-log:query' } },
    { path: 'audit/logins', component: () => import('../views/LoginLogView.vue'), meta: { permission: 'login-log:query' } },
    { path: 'unavailable', component: () => import('../views/UnavailableView.vue') },
    { path: 'forbidden', component: () => import('../views/ForbiddenView.vue') },
  ] },
  { path: '/:pathMatch(.*)*', redirect: '/forbidden' },
] })
router.beforeEach(async to => {
  const auth = useAuth()
  if (to.path === '/login') return true
  if (!getToken()) return '/login'
  const identity = await auth.refresh()
  if (!identity) return getToken() ? (to.path === '/unavailable' ? true : '/unavailable') : '/login'
  if (typeof to.meta.permission === 'string' && !auth.can(to.meta.permission)) return '/forbidden'
  return true
})
