<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { request } from '../api/http'
import type { PermissionNode, RolePermissions } from '../api/types'
import PermissionTree from './PermissionTree.vue'
const visible = defineModel<boolean>({ default: false })
const props = defineProps<{ roleId: string; name: string }>()
const emit = defineEmits<{ saved: [] }>()
const nodes = ref<PermissionNode[]>([])
const selected = ref<string[]>([])
const version = ref('')
const ready = ref(false)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
let generation = 0
watch(visible, async open => {
  const current = ++generation
  if (!open) return
  const roleId = props.roleId
  loading.value = true; ready.value = false; selected.value = []; nodes.value = []; error.value = ''
  try {
    const [permissions, tree] = await Promise.all([request<RolePermissions>('/roles/' + roleId + '/permissions'), request<PermissionNode[]>('/permissions/tree')])
    if (generation !== current || roleId !== props.roleId) return
    selected.value = permissions.permissionIds; version.value = permissions.version; nodes.value = tree; ready.value = true
  } catch (e) { if (generation === current) error.value = e instanceof Error ? e.message : '加载失败' }
  finally { if (generation === current) loading.value = false }
}, { immediate: true })
async function save() {
  saving.value = true; error.value = ''
  try { await request('/roles/' + props.roleId + '/permissions', 'PUT', { version: version.value, permissionIds: selected.value }); visible.value = false; ElMessage.success('角色权限已更新'); emit('saved') }
  catch (e) { error.value = e instanceof Error ? e.message : '授权失败' }
  finally { saving.value = false }
}
</script>
<template><el-dialog v-model="visible" title="分配角色权限" width="620px" :close-on-click-modal="!saving" :show-close="!saving"><p class="muted">{{ name }}：父子权限独立勾选。</p><div v-loading="loading" class="permission-tree"><PermissionTree :nodes="nodes" :selected="selected" :disabled="saving" @update="selected = $event" /></div><p v-if="error" class="form-error" role="alert">{{ error }}</p><template #footer><el-button :disabled="saving" @click="visible = false">取消</el-button><el-button type="primary" :disabled="!ready" :loading="saving" @click="save">保存授权</el-button></template></el-dialog></template>
