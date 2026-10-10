<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElAvatar, ElTable, ElTableColumn, ElPagination, ElSelect, ElOption, ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { request } from '../api/http'
import type { User, Page } from '../api/types'
import { useAuth } from '../stores/auth'
import UserRoleDialog from '../components/UserRoleDialog.vue'
const auth = useAuth()
const router = useRouter()
const rows = ref<User[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const status = ref('')
const loading = ref(false)
const error = ref('')
const saving = ref(false)
const editVisible = ref(false)
const target = ref<User | null>(null)
const detail = ref<User | null>(null)
const detailVisible = ref(false)
const resetVisible = ref(false)
const roleVisible = ref(false)
const roleTarget = ref<User | null>(null)
const newPassword = ref('')
const form = reactive({ username: '', displayName: '', password: '', phone: '', email: '' })
async function load() {
  loading.value = true; error.value = ''
  const query = new URLSearchParams({ page: String(page.value), size: String(size.value), keyword: keyword.value })
  if (status.value !== '') query.set('status', status.value)
  try { const result = await request<Page<User>>('/users?' + query); rows.value = result.items; total.value = result.total }
  catch (e) { error.value = e instanceof Error ? e.message : '查询失败'; rows.value = []; total.value = 0 }
  finally { loading.value = false }
}
function create() { target.value = null; Object.assign(form, { username: '', displayName: '', password: '', phone: '', email: '' }); error.value = ''; editVisible.value = true }
function edit(user: User) { target.value = user; Object.assign(form, { username: user.username, displayName: user.displayName, password: '', phone: user.phone || '', email: user.email || '' }); error.value = ''; editVisible.value = true }
async function save() {
  error.value = ''
  if (!form.displayName.trim() || form.displayName.length > 100) { error.value = '显示名称必填，最长 100 字符'; return }
  if (!target.value && (!form.username || form.username !== form.username.trim() || form.username.length > 64)) { error.value = '账号必填，最长 64 字符且首尾无空白'; return }
  if (!target.value && (!form.password || new TextEncoder().encode(form.password).length > 72)) { error.value = '密码必填，不能超过 72 个 UTF-8 字节'; return }
  if (form.phone.trim() && !/^\+?\d{6,15}$/.test(form.phone.trim())) { error.value = '手机号为可选 + 号后 6～15 位数字'; return }
  if (form.email.trim() && (form.email.trim().length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim()))) { error.value = '请填写有效邮箱'; return }
  saving.value = true
  try {
    const fields = { displayName: form.displayName, phone: form.phone || null, email: form.email || null }
    if (target.value) await request('/users/' + target.value.id, 'PUT', { ...fields, version: target.value.version })
    else await request('/users', 'POST', { ...fields, username: form.username, password: form.password })
    editVisible.value = false; ElMessage.success('用户资料已保存'); await load()
  } catch (e) { error.value = e instanceof Error ? e.message : '保存失败' }
  finally { saving.value = false; form.password = '' }
}
async function showDetail(user: User) {
  try { detail.value = await request<User>('/users/' + user.id); detailVisible.value = true }
  catch (e) { ElMessage.error(e instanceof Error ? e.message : '查询失败') }
}
async function changeStatus(user: User) {
  const next = user.status === 1 ? 0 : 1
  try { await ElMessageBox.confirm(next === 0 ? '停用后，该用户全部已有登录将失效。' : '启用后，该用户可以使用有效密码重新登录。', next === 0 ? '停用用户' : '启用用户', { type: 'warning' }) }
  catch { return }
  try {
    await request('/users/' + user.id + '/status', 'PUT', { version: user.version, status: next })
    ElMessage.success('用户状态已更新'); await load()
  } catch (e) { ElMessage.error(e instanceof Error ? e.message : '操作失败') }
}
function openReset(user: User) { target.value = user; newPassword.value = ''; error.value = ''; resetVisible.value = true }
async function resetPassword() {
  if (!target.value) return
  if (!newPassword.value || new TextEncoder().encode(newPassword.value).length > 72) { error.value = '密码不能为空，不能超过 72 个 UTF-8 字节'; return }
  saving.value = true
  const own = target.value.id === auth.identity?.user.id
  try {
    await request('/users/' + target.value.id + '/password', 'PUT', { version: target.value.version, newPassword: newPassword.value }, !own)
    resetVisible.value = false; ElMessage.success('密码已重置，该用户需要重新登录')
    if (own) { auth.clear(); await router.replace('/login') } else await load()
  } catch (e) { error.value = e instanceof Error ? e.message : '重置失败' }
  finally { saving.value = false; newPassword.value = '' }
}
onMounted(load)
</script>
<template><div><div class="page-heading"><div><h1>用户管理</h1></div><el-button v-if="auth.can('user:create')" type="primary" @click="create">新增用户</el-button></div>
  <section class="table-panel"><div class="list-heading"><h2>用户列表</h2><span role="status">{{ loading ? "正在读取…" : error ? "读取失败" : "共 " + total + " 条记录" }}</span></div><div class="filters"><el-input v-model="keyword" aria-label="搜索账号或显示名称" placeholder="搜索账号或显示名称" clearable style="width:260px" maxlength="100" @keyup.enter="page = 1; load()" /><el-select v-model="status" aria-label="用户状态筛选" style="width:140px"><el-option value="" label="全部状态" /><el-option value="1" label="启用" /><el-option value="0" label="停用" /></el-select><el-button type="primary" :disabled="loading" @click="page = 1; load()">查询</el-button><el-button :disabled="loading" @click="keyword = ''; status = ''; page = 1; load()">重置</el-button></div>
    <p v-if="error && !editVisible && !resetVisible" class="form-error" role="alert">{{ error }}</p><el-table v-loading="loading" :data="rows" row-key="id" style="width:100%" :empty-text="loading ? '正在读取…' : error ? '暂时无法读取，请重新查询' : '暂无用户'">
<!-- @vue-generic {User} -->
<el-table-column label="账号" min-width="140"><template #default="{ row: user }"><div class="account-cell"><el-avatar :size="28" aria-hidden="true">{{ user.displayName.slice(0, 1) }}</el-avatar><strong>{{ user.username }}</strong></div></template></el-table-column>
<!-- @vue-generic {User} -->
<el-table-column label="显示名称" min-width="140"><template #default="{ row: user }">{{ user.displayName }}</template></el-table-column>
<!-- @vue-generic {User} -->
<el-table-column label="状态" min-width="140"><template #default="{ row: user }"><el-tag :type="user.status === 1 ? 'success' : 'info'">{{ user.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
<!-- @vue-generic {User} -->
<el-table-column label="角色" min-width="140"><template #default="{ row: user }">{{ user.superAdmin ? '超级管理员' : user.roles.map((role: { name: string }) => role.name).join('、') || '未分配' }}</template></el-table-column>
<!-- @vue-generic {User} -->
<el-table-column label="联系资料" min-width="180"><template #default="{ row: user }"><div>{{ user.phone || '—' }}</div><small class="muted">{{ user.email || '—' }}</small></template></el-table-column>
<!-- @vue-generic {User} -->
<el-table-column label="操作" min-width="340" fixed="right"><template #default="{ row: user }"><el-button link type="primary" @click="showDetail(user)">详情</el-button><el-button v-if="auth.can('user:edit')" link type="primary" @click="edit(user)">编辑</el-button><el-button v-if="auth.can('user:status')" link :disabled="user.superAdmin" @click="changeStatus(user)">{{ user.status === 1 ? '停用' : '启用' }}</el-button><el-button v-if="auth.can('user:assign-role')" link type="primary" @click="roleTarget = user; roleVisible = true">分配角色</el-button><el-button v-if="auth.can('user:reset-password')" link @click="openReset(user)">重置密码</el-button></template></el-table-column></el-table><div class="pagination"><el-pagination v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50, 100]" :total="total" :disabled="loading" :pager-count="5" layout="total, sizes, prev, pager, next" background @change="load" /></div></section>
  <el-dialog v-model="editVisible" :title="target ? '编辑用户资料' : '新增用户'" width="500px" :close-on-click-modal="!saving" :show-close="!saving"><el-form label-position="top">
    <el-form-item label="账号" for="userview-form-username"><el-input id="userview-form-username" v-model="form.username" :disabled="!!target" maxlength="64" /><small class="muted">创建后不可修改，账号区分大小写</small></el-form-item><el-form-item label="显示名称" for="userview-form-displayName"><el-input id="userview-form-displayName" v-model="form.displayName" maxlength="100" /></el-form-item><el-form-item v-if="!target" label="初始密码"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" /></el-form-item><el-form-item label="手机号（选填）" for="userview-form-phone"><el-input id="userview-form-phone" v-model="form.phone" /></el-form-item><el-form-item label="邮箱（选填）" for="userview-form-email"><el-input id="userview-form-email" v-model="form.email" /></el-form-item><p v-if="error" class="form-error" role="alert">{{ error }}</p>
  </el-form><template #footer><el-button :disabled="saving" @click="editVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template></el-dialog>
  <el-dialog v-model="resetVisible" title="重置用户密码" width="440px" :close-on-click-modal="!saving" :show-close="!saving"><p class="muted">将重置 {{ target?.username }} 的密码。成功后该用户全部已有登录失效。</p><label class="field-label" for="reset-password">新密码</label><el-input id="reset-password" v-model="newPassword" type="password" show-password autocomplete="new-password" placeholder="输入新密码" /><p v-if="error" class="form-error" role="alert">{{ error }}</p><template #footer><el-button :disabled="saving" @click="resetVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="resetPassword">确认重置</el-button></template></el-dialog>
  <el-dialog v-model="detailVisible" title="用户详情" width="560px"><el-descriptions v-if="detail" :column="1" border><el-descriptions-item label="账号">{{ detail.username }}</el-descriptions-item><el-descriptions-item label="显示名称">{{ detail.displayName }}</el-descriptions-item><el-descriptions-item label="手机号">{{ detail.phone || '—' }}</el-descriptions-item><el-descriptions-item label="邮箱">{{ detail.email || '—' }}</el-descriptions-item><el-descriptions-item label="状态">{{ detail.status === 1 ? '启用' : '停用' }}</el-descriptions-item><el-descriptions-item label="角色">{{ detail.roles.map(role => role.name).join('、') || '未分配' }}</el-descriptions-item><el-descriptions-item label="创建时间">{{ new Date(detail.createdAt).toLocaleString() }}</el-descriptions-item><el-descriptions-item label="更新时间">{{ new Date(detail.updatedAt).toLocaleString() }}</el-descriptions-item></el-descriptions></el-dialog>
<UserRoleDialog v-if="roleTarget" v-model="roleVisible" :user-id="roleTarget.id" :username="roleTarget.username" @saved="load" /></div></template>
