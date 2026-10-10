<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElCheckbox, ElCheckboxGroup } from 'element-plus'
import { request } from '../api/http'
import type { RoleOption, UserRoles } from '../api/types'
const visible = defineModel<boolean>({ default: false })
const props = defineProps<{ userId: string; username: string }>()
const emit = defineEmits<{ saved: [] }>()
const options = ref<RoleOption[]>([])
const selected = ref<string[]>([])
const version = ref('')
const loading = ref(false)
const saving = ref(false)
const ready = ref(false)
const error = ref('')
let generation = 0
watch(visible, async open => {
  const current = ++generation
  if (!open) return
  const userId = props.userId
  loading.value = true; ready.value = false; selected.value = []; options.value = []; error.value = ''
  try {
    const [roles, choices] = await Promise.all([request<UserRoles>('/users/' + userId + '/roles'), request<RoleOption[]>('/role-options')])
    if (generation !== current || userId !== props.userId) return
    selected.value = roles.roleIds; version.value = roles.version; options.value = choices; ready.value = true
  } catch (e) { if (generation === current) error.value = e instanceof Error ? e.message : '加载失败' }
  finally { if (generation === current) loading.value = false }
}, { immediate: true })
async function save() {
  saving.value = true; error.value = ''
  try { await request('/users/' + props.userId + '/roles', 'PUT', { version: version.value, roleIds: selected.value }); visible.value = false; ElMessage.success('用户角色已更新'); emit('saved') }
  catch (e) { error.value = e instanceof Error ? e.message : '授权失败' }
  finally { saving.value = false }
}
</script>
<template><el-dialog v-model="visible" title="分配用户角色" width="520px" :close-on-click-modal="!saving" :show-close="!saving"><p class="muted">{{ username }} 的有效权限来自全部已选角色的并集。取消全部选择将撤销角色授权。</p><div v-loading="loading" class="choice-list"><el-checkbox-group v-model="selected"><div v-for="role in options" :key="role.id" class="choice-row"><el-checkbox :value="role.id" :disabled="saving">{{ role.name }}</el-checkbox></div></el-checkbox-group><p v-if="!loading && !options.length" class="muted">暂无可分配角色</p></div><p v-if="error" class="form-error" role="alert">{{ error }}</p><template #footer><el-button :disabled="saving" @click="visible = false">取消</el-button><el-button type="primary" :disabled="!ready" :loading="saving" @click="save">保存授权</el-button></template></el-dialog></template>
