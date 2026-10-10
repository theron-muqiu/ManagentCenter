<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElTabs, ElTabPane } from 'element-plus'
import { useAuth } from '../stores/auth'
import { request } from '../api/http'
import type { Page, OperationLog, LoginLog } from '../api/types'
import { User, Key, Document, Unlock, ArrowRight, Refresh, Lock, Calendar, CircleCheck, Clock } from '@element-plus/icons-vue'
import { pageInfo } from '../ui/navigation'
const auth = useAuth()
const busy = ref(false)
const updatedAt = ref<Date | null>(null)
const metricDefinitions = [
  { key: 'users', name: '平台用户', permission: 'user:query', path: '/users?page=1&size=1', route: '/system/users', icon: User, note: '账号与登录状态' },
  { key: 'roles', name: '权限角色', permission: 'role:query', path: '/roles?page=1&size=1', route: '/system/roles', icon: Key, note: '按职责组织访问权限' },
  { key: 'operations', name: '操作记录', permission: 'operation-log:query', path: '/operation-logs?page=1&size=5', route: '/audit/operations', icon: Document, note: '关键变更与处理结果' },
  { key: 'logins', name: '登录日志', permission: 'login-log:query', path: '/login-logs?page=1&size=5', route: '/audit/logins', icon: Unlock, note: '账号登录活动' },
] as const
const summaries = reactive<Record<string, { total: number | null; error: string }>>(Object.fromEntries(metricDefinitions.map(metric => [metric.key, { total: null, error: '' }])))
const operations = ref<OperationLog[]>([])
const logins = ref<LoginLog[]>([])
const activityTab = ref('operations')
const activityMetrics = computed(() => metricDefinitions.filter(metric => ['operations', 'logins'].includes(metric.key) && auth.can(metric.permission)))
const shortcuts = computed(() => auth.identity?.menus.flatMap(group => group.children.map(item => ({ ...item, group: group.name }))) ?? [])
const currentError = computed(() => summaries[activityTab.value]?.error)
let generation = 0
let mounted = true
async function load() {
  const current = ++generation
  busy.value = true
  await Promise.all(metricDefinitions.map(async metric => {
    if (!auth.can(metric.permission)) {
      summaries[metric.key] = { total: null, error: '' }
      if (metric.key === 'operations') operations.value = []
      if (metric.key === 'logins') logins.value = []
      return
    }
    summaries[metric.key]!.error = ''
    try {
      const data = await request<Page<OperationLog | LoginLog>>(metric.path)
      if (!mounted || current !== generation || !auth.can(metric.permission)) return
      summaries[metric.key]!.total = data.total
      if (metric.key === 'operations') operations.value = data.items as OperationLog[]
      if (metric.key === 'logins') logins.value = data.items as LoginLog[]
    } catch (e) {
      if (!mounted || current !== generation) return
      summaries[metric.key] = { total: null, error: e instanceof Error ? e.message : '暂时无法读取' }
      if (metric.key === 'operations') operations.value = []
      if (metric.key === 'logins') logins.value = []
    }
  }))
  if (mounted && current === generation) { busy.value = false; updatedAt.value = new Date() }
}
watch(activityMetrics, metrics => { if (!metrics.some(metric => metric.key === activityTab.value)) activityTab.value = metrics[0]?.key ?? 'operations' }, { immediate: true })
watch(() => metricDefinitions.map(metric => auth.can(metric.permission)).join(','), () => { void load() })
onMounted(load)
onUnmounted(() => { mounted = false; generation++ })
</script>
<template>
  <div class="dashboard">
    <div class="page-heading"><div><h1>工作台</h1><p class="muted">欢迎回来，{{ auth.identity?.user.displayName }}。</p></div><div class="dashboard-date"><Calendar aria-hidden="true" />{{ new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }) }}</div></div>
    <section class="metrics-grid" aria-label="平台数据概览">
      <article v-for="metric in metricDefinitions" :key="metric.key" class="metric-card"><div class="metric-top"><span>{{ metric.name }}</span><span class="metric-icon" :class="'metric-' + metric.key"><component :is="metric.icon" aria-hidden="true" /></span></div><div class="metric-value" :class="{ 'metric-message': !auth.can(metric.permission) || summaries[metric.key]!.error }"><span v-if="!auth.can(metric.permission)">无查看权限</span><span v-else-if="busy && summaries[metric.key]!.total === null" class="number-skeleton" aria-label="正在读取"></span><span v-else-if="summaries[metric.key]!.error">暂时无法读取</span><span v-else>{{ summaries[metric.key]!.total?.toLocaleString() ?? '—' }}</span></div><div class="metric-bottom"><span>{{ metric.note }}</span><router-link v-if="auth.can(metric.permission)" :to="metric.route" :aria-label="'查看' + metric.name"><ArrowRight aria-hidden="true" /></router-link><Lock v-else aria-hidden="true" /></div></article>
    </section>
    <div class="dashboard-columns">
      <section class="activity-panel surface-panel"><div class="panel-heading"><div><h2>最近活动</h2></div><button class="icon-button" aria-label="刷新平台概览" :disabled="busy" @click="load"><Refresh aria-hidden="true" :class="{ spinning: busy }" /></button></div><div v-if="!activityMetrics.length" class="panel-empty"><Lock aria-hidden="true" /><h3>暂无日志查看权限</h3></div><el-tabs v-else v-model="activityTab" class="activity-tabs"><el-tab-pane v-for="metric in activityMetrics" :key="metric.key" :label="metric.name" :name="metric.key"><div v-if="activityTab === metric.key" :aria-busy="busy">
        <div v-if="!activityMetrics.length" class="panel-empty"><Lock aria-hidden="true" /><h3>暂未开放日志查看</h3><p>如需查看平台活动，请联系管理员分配日志查看权限。</p></div>
        <div v-else-if="currentError" class="panel-empty"><Document aria-hidden="true" /><h3>活动暂时无法读取</h3><p role="alert">{{ currentError }}</p><el-button :disabled="busy" @click="load">重新加载</el-button></div>
        <div v-else-if="busy && !updatedAt" class="activity-loading" role="status">正在读取最近活动…</div>
        <template v-else-if="activityTab === 'operations'"><div v-for="event in operations" :key="event.id" class="activity-row"><span class="activity-symbol" :class="{ failed: event.result === 0 }"><Document aria-hidden="true" /></span><div class="activity-copy"><strong>{{ event.feature }}</strong><p>{{ event.operatorDisplayName || event.operatorUsername || '未认证请求' }}<span>·</span>{{ event.operatorUsername || '未认证' }}</p></div><div class="activity-meta"><span class="result-label" :class="{ failed: event.result === 0 }">{{ event.result === 1 ? '成功' : '失败' }}</span><time :datetime="event.occurredAt">{{ new Date(event.occurredAt).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) }}</time></div></div><div v-if="!operations.length" class="panel-empty"><Document aria-hidden="true" /><h3>还没有操作记录</h3><p>关键管理操作的处理结果将在这里显示。</p></div></template>
        <template v-else><div v-for="event in logins" :key="event.id" class="activity-row"><span class="activity-symbol" :class="{ failed: event.result === 0 }"><Unlock aria-hidden="true" /></span><div class="activity-copy"><strong>{{ event.attemptedUsername || '未提供有效账号' }}</strong><p>{{ event.result === 1 ? '登录成功' : event.failureReason || '登录失败' }}</p></div><div class="activity-meta"><span class="result-label" :class="{ failed: event.result === 0 }">{{ event.result === 1 ? '成功' : '失败' }}</span><time :datetime="event.occurredAt">{{ new Date(event.occurredAt).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) }}</time></div></div><div v-if="!logins.length" class="panel-empty"><Unlock aria-hidden="true" /><h3>还没有登录日志</h3><p>账号的登录活动将在这里显示。</p></div></template>
      </div></el-tab-pane></el-tabs><router-link v-if="activityMetrics.length" class="panel-footer-link" :to="activityTab === 'operations' ? '/audit/operations' : '/audit/logins'">查看全部{{ activityTab === 'operations' ? '操作记录' : '登录日志' }}<ArrowRight aria-hidden="true" /></router-link></section>
      <div class="dashboard-side"><section class="surface-panel shortcuts-panel"><div class="panel-heading"><div><h2>快捷入口</h2></div></div><router-link v-for="item in shortcuts" :key="item.code" :to="item.routePath || '/'" class="shortcut-row"><span class="shortcut-icon"><component :is="pageInfo[item.routePath as keyof typeof pageInfo]?.icon || Document" aria-hidden="true" /></span><span><strong>{{ item.name }}</strong></span><ArrowRight aria-hidden="true" /></router-link><div v-if="!shortcuts.length" class="panel-empty"><Key aria-hidden="true" /><h3>暂未分配管理功能</h3><p>你仍可以通过右上角账号菜单修改密码或退出登录。</p></div></section><section class="identity-panel"><span class="identity-icon"><Lock aria-hidden="true" /></span><h3>当前账号</h3><strong class="identity-name">{{ auth.identity?.user.username }}</strong><p>{{ auth.identity?.user.superAdmin ? '超级管理员 · 拥有全部管理权限' : '平台成员 · 按角色权限访问' }}</p><div class="identity-role-list"><span v-for="role in auth.identity?.user.roles" :key="role.id">{{ role.name }}</span><span v-if="!auth.identity?.user.superAdmin && !auth.identity?.user.roles.length">暂未分配角色</span></div><div class="session-expiry"><Clock aria-hidden="true" /><span>本次登录到期时间<small>{{ auth.identity ? new Date(auth.identity.expiresAt).toLocaleString('zh-CN') : '暂不可用' }}</small></span></div></section></div>
    </div><p v-if="updatedAt" class="dashboard-update">最近读取：{{ updatedAt.toLocaleTimeString('zh-CN') }}</p>
  </div>
</template>
