<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElTable, ElTableColumn, ElPagination, ElSelect, ElOption, ElMessage, ElMessageBox } from 'element-plus'
import { request } from '../api/http'
import type { Role, Page } from '../api/types'
import { useAuth } from '../stores/auth'
import RolePermissionDialog from '../components/RolePermissionDialog.vue'
const auth = useAuth()
const rows = ref<Role[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const loading = ref(false)
const error = ref('')
const saving = ref(false)
const visible = ref(false)
const target = ref<Role | null>(null)
const detail = ref<Role | null>(null)
const detailVisible = ref(false)
const permissionVisible = ref(false)
const permissionTarget = ref<Role | null>(null)
const form = reactive({ name: '', description: '' })
async function load() {
  loading.value = true; error.value = ''
  try {
    const query = new URLSearchParams({ page: String(page.value), size: String(size.value), keyword: keyword.value })
    const result = await request<Page<Role>>('/roles?' + query); rows.value = result.items; total.value = result.total
  } catch (e) { error.value = e instanceof Error ? e.message : '查询失败'; rows.value = []; total.value = 0 }
  finally { loading.value = false }
}
function create() { target.value = null; Object.assign(form, { name: '', description: '' }); error.value = ''; visible.value = true }
function edit(role: Role) { target.value = role; Object.assign(form, { name: role.name, description: role.description || '' }); error.value = ''; visible.value = true }
async function save() {
  if (!form.name || form.name !== form.name.trim() || form.name.length > 100) { error.value = '名称必填，最长 100 字符，首尾不能有空白'; return }
  if (form.description.length > 500) { error.value = '说明最长 500 字符'; return }
  saving.value = true; error.value = ''
  try {
    const body = { name: form.name, description: form.description || null }
    if (target.value) await request('/roles/' + target.value.id, 'PUT', { ...body, version: target.value.version })
    else await request('/roles', 'POST', body)
    visible.value = false; ElMessage.success('角色资料已保存'); await load()
  } catch (e) { error.value = e instanceof Error ? e.message : '保存失败' }
  finally { saving.value = false }
}
async function remove(role: Role) {
  try { await ElMessageBox.confirm('仅无用户关联的角色可以删除。删除后，该角色的权限配置也会移除。', '删除角色', { type: 'warning' }) }
  catch { return }
  try { await request('/roles/' + role.id + '?version=' + role.version, 'DELETE'); ElMessage.success('角色已删除'); await load() }
  catch (e) { ElMessage.error(e instanceof Error ? e.message : '删除失败') }
}
async function showDetail(role: Role) {
  try { detail.value = await request<Role>('/roles/' + role.id); detailVisible.value = true }
  catch (e) { ElMessage.error(e instanceof Error ? e.message : '查询失败') }
}
onMounted(load)
</script>
<template><div><div class="page-heading"><div><h1>角色管理</h1></div><el-button v-if="auth.can('role:create')" type="primary" @click="create">新增角色</el-button></div>
  <section class="table-panel"><div class="list-heading"><h2>角色列表</h2><span role="status">{{ loading ? "正在读取…" : error ? "读取失败" : "共 " + total + " 条记录" }}</span></div><div class="filters"><el-input v-model="keyword" aria-label="搜索角色名称" placeholder="搜索角色名称" clearable style="width:260px" maxlength="100" @keyup.enter="page = 1; load()" /><el-button type="primary" :disabled="loading" @click="page = 1; load()">查询</el-button><el-button :disabled="loading" @click="keyword = ''; page = 1; load()">重置</el-button></div><p v-if="error && !visible" class="form-error" role="alert">{{ error }}</p>
    <el-table v-loading="loading" :data="rows" row-key="id" style="width:100%" :empty-text="loading ? '正在读取…' : error ? '暂时无法读取，请重新查询' : '暂无角色'">
<!-- @vue-generic {Role} -->
<el-table-column label="角色名称" min-width="140"><template #default="{ row: role }">{{ role.name }}</template></el-table-column>
<!-- @vue-generic {Role} -->
<el-table-column label="说明" min-width="180"><template #default="{ row: role }">{{ role.description || '—' }}</template></el-table-column>
<!-- @vue-generic {Role} -->
<el-table-column label="授权范围" min-width="140"><template #default="{ row: role }">{{ role.permissionCodes.length ? role.permissionCodes.length + ' 项权限' : '未配置权限' }}</template></el-table-column>
<!-- @vue-generic {Role} -->
<el-table-column label="操作" min-width="260" fixed="right"><template #default="{ row: role }"><el-button link type="primary" @click="showDetail(role)">详情</el-button><el-button v-if="auth.can('role:edit')" link type="primary" @click="edit(role)">编辑</el-button><el-button v-if="auth.can('role:assign-permission')" link type="primary" @click="permissionTarget = role; permissionVisible = true">分配权限</el-button><el-button v-if="auth.can('role:delete')" link type="danger" @click="remove(role)">删除</el-button></template></el-table-column></el-table>
    <div class="pagination"><el-pagination v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50, 100]" :total="total" :disabled="loading" :pager-count="5" layout="total, sizes, prev, pager, next" background @change="load" /></div>
  </section><el-dialog v-model="visible" :title="target ? '编辑角色资料' : '新增角色'" width="500px" :close-on-click-modal="!saving" :show-close="!saving"><el-form label-position="top"><el-form-item label="角色名称" for="roleview-form-name"><el-input id="roleview-form-name" v-model="form.name" maxlength="100" /></el-form-item><el-form-item label="角色说明（选填）" for="roleview-form-description"><el-input id="roleview-form-description" v-model="form.description" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item><p v-if="error" class="form-error" role="alert">{{ error }}</p></el-form><template #footer><el-button :disabled="saving" @click="visible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template></el-dialog>
  <el-dialog v-model="detailVisible" title="角色详情" width="600px"><template v-if="detail"><el-descriptions :column="1" border><el-descriptions-item label="名称">{{ detail.name }}</el-descriptions-item><el-descriptions-item label="说明">{{ detail.description || '—' }}</el-descriptions-item><el-descriptions-item label="创建时间">{{ new Date(detail.createdAt).toLocaleString() }}</el-descriptions-item></el-descriptions><h3>已授权代码</h3><p v-if="!detail.permissionCodes.length" class="muted">尚未配置权限</p><div class="permission-tags"><el-tag v-for="code in detail.permissionCodes" :key="code" type="info">{{ code }}</el-tag></div></template></el-dialog>
<RolePermissionDialog v-if="permissionTarget" v-model="permissionVisible" :role-id="permissionTarget.id" :name="permissionTarget.name" @saved="load" /></div></template>
