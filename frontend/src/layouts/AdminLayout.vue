<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, watch, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElDrawer, ElDropdown, ElDropdownMenu, ElDropdownItem, ElAvatar, ElBreadcrumb, ElBreadcrumbItem } from 'element-plus'
import { useAuth } from '../stores/auth'
import ChangePasswordDialog from '../components/ChangePasswordDialog.vue'
import SystemNavigation from '../components/SystemNavigation.vue'
import { ArrowDown, ArrowRight, Close, Fold, Expand, House, Menu, Refresh, Lock, SwitchButton, Grid } from '@element-plus/icons-vue'
import { pageInfo } from '../ui/navigation'
const auth = useAuth()
const route = useRoute()
const router = useRouter()
const busy = ref(false)
const passwordVisible = ref(false)
const collapsed = ref(false)
const mobileOpen = ref(false)
const drawerRoute = ref(route.fullPath)
const menuToggle = ref<HTMLButtonElement | null>(null)
const main = ref<HTMLElement | null>(null)
const media = window.matchMedia?.('(max-width: 900px)')
const isMobile = ref(media?.matches ?? false)
const currentPage = computed(() => pageInfo[route.path as keyof typeof pageInfo] ?? pageInfo['/'])
async function logout() { busy.value = true; try { await auth.logout(); await router.push('/login') } catch (e) { ElMessage.error(e instanceof Error ? e.message : '退出失败') } finally { busy.value = false } }
async function focus() { await auth.refresh(); if (!auth.identity) await router.replace(auth.unavailable ? '/unavailable' : '/login') }
watch(() => auth.identity, identity => {
  if (!identity) void router.replace(auth.unavailable ? '/unavailable' : '/login')
  else if (identity && typeof route.meta.permission === 'string' && !auth.can(route.meta.permission)) void router.replace('/forbidden')
})
watch(() => route.fullPath, async () => {
  mobileOpen.value = false
  await nextTick(); main.value?.focus()
})
function resize(event: MediaQueryListEvent) { isMobile.value = event.matches; if (!event.matches) mobileOpen.value = false }
onMounted(() => { window.addEventListener('focus', focus); media?.addEventListener('change', resize) })
onUnmounted(() => {
  window.removeEventListener('focus', focus); media?.removeEventListener('change', resize)
})
</script>
<template>
  <div class="admin-shell" :class="{ 'nav-collapsed': collapsed, 'mobile-nav-open': mobileOpen }">
    <a href="#main-content" class="skip-link" :inert="mobileOpen">跳到主内容</a>
    <aside v-if="!isMobile" class="sidebar" aria-label="主导航"><SystemNavigation :collapsed="collapsed" /></aside>
    <el-drawer v-if="isMobile" v-model="mobileOpen" title="导航" direction="ltr" size="272px" append-to-body class="navigation-drawer" @open="drawerRoute = route.fullPath" @closed="route.fullPath === drawerRoute ? menuToggle?.focus() : main?.focus()"><SystemNavigation /></el-drawer>
    <div class="workspace" :inert="mobileOpen">
      <header class="topbar">
        <div class="topbar-left">
          <button ref="menuToggle" class="icon-button mobile-toggle" aria-label="打开导航" aria-controls="primary-navigation" :aria-expanded="mobileOpen" @click="mobileOpen = true"><Menu aria-hidden="true" /></button>
          <button class="icon-button desktop-toggle" :aria-label="collapsed ? '展开导航' : '收起导航'" :aria-expanded="!collapsed" @click="collapsed = !collapsed"><component :is="collapsed ? Expand : Fold" aria-hidden="true" /></button>
          <el-breadcrumb :separator-icon="ArrowRight"><el-breadcrumb-item>{{ currentPage.section }}</el-breadcrumb-item><el-breadcrumb-item>{{ currentPage.name }}</el-breadcrumb-item></el-breadcrumb>
        </div>
        <div class="account">
          <el-button circle :icon="Refresh" aria-label="刷新访问权限" @click="focus" />
          <el-dropdown trigger="click" @command="(command: string) => command === 'password' ? passwordVisible = true : logout()">
            <el-button text class="account-trigger"><el-avatar :size="28">{{ (auth.identity?.user.displayName || 'M').slice(0, 1) }}</el-avatar><span class="account-copy">{{ auth.identity?.user.displayName }}</span><ArrowDown aria-hidden="true" /></el-button>
            <template #dropdown><el-dropdown-menu><el-dropdown-item v-if="auth.identity" command="password" :icon="Lock">修改密码</el-dropdown-item><el-dropdown-item command="logout" :icon="SwitchButton" :disabled="busy" divided>退出登录</el-dropdown-item></el-dropdown-menu></template>
          </el-dropdown>
        </div>
      </header>
      <main ref="main" id="main-content" class="page-content" tabindex="-1"><router-view /></main>
      <footer class="workspace-footer"><span>ManagentCenter</span></footer>
    </div>
    <ChangePasswordDialog v-model="passwordVisible" />
  </div>
</template>
