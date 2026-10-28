import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import i18n from '../i18n'
import { useCurrentUser } from '../composables/useCurrentUser'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue')
  },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue') },
      { path: 'bill', name: 'Bill', component: () => import('../views/Bill.vue') },
      { path: 'account', name: 'Account', component: () => import('../views/Account.vue') },
      { path: 'recurring', name: 'Recurring', component: () => import('../views/Recurring.vue') },
      { path: 'assets', name: 'Assets', component: () => import('../views/Assets.vue') },
      { path: 'admin', name: 'Admin', meta: { admin: true }, component: () => import('../views/Admin.vue') },
      { path: 'notifications', name: 'Notifications', component: () => import('../views/Notifications.vue') },
      { path: 'category', name: 'Category', component: () => import('../views/Category.vue') },
      { path: 'budget', name: 'Budget', component: () => import('../views/Budget.vue') },
      { path: 'stats', name: 'Stats', component: () => import('../views/Stats.vue') },
      { path: 'profile', name: 'Profile', component: () => import('../views/Profile.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, from, next) => {
  const token = localStorage.getItem('token')
  if (!token && to.path !== '/login' && to.path !== '/register') {
    next('/login')
  } else if (to.meta.admin) {
    try { const user = await useCurrentUser().loadProfile(); if (user.role === 'ADMIN') next(); else { ElMessage.warning(i18n.global.t('admin.noPermission')); next('/dashboard') } }
    catch { next('/login') }
  } else {
    next()
  }
})

router.onError(() => {
  ElMessage.error(i18n.global.t('common.pageLoadFailed'))
})

export default router
