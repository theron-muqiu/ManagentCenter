<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElTable, ElTableColumn, ElPagination, ElSelect, ElOption, ElDatePicker, ElMessage } from 'element-plus'
import { request } from '../api/http'
import type { OperationLog, OperationLogDetail, Page } from '../api/types'
const rows = ref<OperationLog[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const username = ref('')
const result = ref('')
const operationType = ref('')
const startTime = ref('')
const endTime = ref('')
const loading = ref(false)
const error = ref('')
const detail = ref<OperationLogDetail | null>(null)
const visible = ref(false)
const types = ['新增', '编辑', '启停', '删除', '本人改密', '重置密码', '分配角色', '分配权限']
async function load() {
  error.value = ''
  if ([startTime.value, endTime.value].some(value => value && !Number.isFinite(new Date(value).getTime()))) { error.value = '请输入有效时间'; return }
  if (startTime.value && endTime.value && new Date(endTime.value).getTime() <= new Date(startTime.value).getTime()) { error.value = '结束时间必须晚于开始时间'; return }
  loading.value = true
  const query = new URLSearchParams({ page: String(page.value), size: String(size.value) })
  if (username.value) query.set('operatorUsername', username.value)
  if (result.value !== '') query.set('result', result.value)
  if (operationType.value !== '') query.set('operationType', operationType.value)
  if (startTime.value) query.set('startTime', new Date(startTime.value).toISOString())
  if (endTime.value) query.set('endTime', new Date(endTime.value).toISOString())
  try { const data = await request<Page<OperationLog>>('/operation-logs?' + query); rows.value = data.items; total.value = data.total }
  catch (e) { error.value = e instanceof Error ? e.message : '查询失败'; rows.value = []; total.value = 0 }
  finally { loading.value = false }
}
async function showDetail(row: OperationLog) {
  try { detail.value = await request<OperationLogDetail>('/operation-logs/' + row.id); visible.value = true }
  catch (e) { ElMessage.error(e instanceof Error ? e.message : '查询失败') }
}
onMounted(load)
</script>
<template><div><div class="page-heading"><div><h1>操作记录</h1></div></div>
  <section class="table-panel"><div class="list-heading"><h2>操作记录列表</h2><span role="status">{{ loading ? "正在读取…" : error ? "读取失败" : "共 " + total + " 条记录" }}</span></div><div class="filters"><el-input v-model="username" aria-label="筛选账号" placeholder="操作账号（精确匹配）" maxlength="64" clearable style="width:220px" @keyup.enter="page = 1; load()" /><el-select v-model="operationType" style="width:140px" aria-label="操作类型"><el-option value="" label="全部操作" /><el-option v-for="(name, index) in types" :key="index" :value="String(index)" :label="name" /></el-select><el-select v-model="result" style="width:140px" aria-label="操作结果"><el-option value="" label="全部结果" /><el-option value="1" label="成功" /><el-option value="0" label="失败" /></el-select></div>
    <el-form inline class="filters date-filters"><el-form-item label="开始时间" for="operationlogview-startTime"><el-date-picker id="operationlogview-startTime" v-model="startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" aria-label="开始时间" placeholder="开始时间" style="width:190px" /></el-form-item><el-form-item label="结束时间" for="operationlogview-endTime"><el-date-picker id="operationlogview-endTime" v-model="endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" aria-label="结束时间" placeholder="结束时间" style="width:190px" /></el-form-item><el-form-item><el-button type="primary" :disabled="loading" @click="page = 1; load()">查询</el-button><el-button :disabled="loading" @click="username = ''; result = ''; startTime = ''; endTime = ''; operationType = ''; page = 1; load()">重置</el-button></el-form-item><el-form-item><small class="muted">使用本地时间，包含开始、不包含结束</small></el-form-item></el-form><p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <el-table v-loading="loading" :data="rows" row-key="id" style="width:100%" :empty-text="loading ? '正在读取…' : error ? '暂时无法读取，请重新查询' : '暂无操作记录'">
<!-- @vue-generic {OperationLog} -->
<el-table-column label="操作时间" min-width="180"><template #default="{ row: row }">{{ new Date(row.occurredAt).toLocaleString() }}</template></el-table-column>
<!-- @vue-generic {OperationLog} -->
<el-table-column label="操作人" min-width="140"><template #default="{ row: row }">{{ row.operatorUsername || '未认证' }}<small v-if="row.operatorDisplayName" class="cell-note">{{ row.operatorDisplayName }}</small></template></el-table-column>
<!-- @vue-generic {OperationLog} -->
<el-table-column label="功能" min-width="140"><template #default="{ row: row }">{{ row.feature }}<small class="cell-note">{{ types[row.operationType] }}</small></template></el-table-column>
<!-- @vue-generic {OperationLog} -->
<el-table-column label="结果" min-width="140"><template #default="{ row: row }"><el-tag :type="row.result === 1 ? 'success' : 'danger'">{{ row.result === 1 ? '成功' : '失败' }}</el-tag><small v-if="row.failureReason" class="cell-note">{{ row.failureReason }}</small></template></el-table-column>
<!-- @vue-generic {OperationLog} -->
<el-table-column label="来源 IP" min-width="140"><template #default="{ row: row }">{{ row.clientIp || '—' }}</template></el-table-column>
<!-- @vue-generic {OperationLog} -->
<el-table-column label="耗时" min-width="140"><template #default="{ row: row }">{{ row.durationMs }} ms</template></el-table-column>
<!-- @vue-generic {OperationLog} -->
<el-table-column label="操作" min-width="90" fixed="right"><template #default="{ row: row }"><el-button link type="primary" @click="showDetail(row)">详情</el-button></template></el-table-column></el-table>
    <div class="pagination"><el-pagination v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50, 100]" :total="total" :disabled="loading" :pager-count="5" layout="total, sizes, prev, pager, next" background @change="load" /></div>
  </section><el-dialog v-model="visible" title="操作记录详情" width="720px"><template v-if="detail"><el-descriptions :column="2" border><el-descriptions-item label="功能">{{ detail.feature }}</el-descriptions-item><el-descriptions-item label="结果">{{ detail.result === 1 ? '成功' : '失败' }}</el-descriptions-item><el-descriptions-item label="操作账号">{{ detail.operatorUsername || '未认证' }}</el-descriptions-item><el-descriptions-item label="目标">{{ detail.targetId ? (detail.targetType === 0 ? '用户 ' : '角色 ') + detail.targetId : '—' }}</el-descriptions-item><el-descriptions-item label="发生时间">{{ new Date(detail.occurredAt).toLocaleString() }}</el-descriptions-item><el-descriptions-item label="写入时间">{{ new Date(detail.createdAt).toLocaleString() }}</el-descriptions-item><el-descriptions-item label="来源 IP">{{ detail.clientIp || '—' }}</el-descriptions-item><el-descriptions-item label="耗时">{{ detail.durationMs }} ms</el-descriptions-item><el-descriptions-item label="错误码">{{ detail.errorCode }}</el-descriptions-item><el-descriptions-item label="失败原因">{{ detail.failureReason || '—' }}</el-descriptions-item><el-descriptions-item label="请求" :span="2">{{ detail.requestMethod }} {{ detail.requestPath }}</el-descriptions-item><el-descriptions-item label="请求标识" :span="2">{{ detail.requestId }}</el-descriptions-item><el-descriptions-item label="浏览器信息" :span="2">{{ detail.userAgent || '—' }}</el-descriptions-item></el-descriptions><h3>操作摘要</h3><pre class="audit-summary">{{ JSON.stringify(detail.summary, null, 2) }}</pre></template></el-dialog>
</div></template>
