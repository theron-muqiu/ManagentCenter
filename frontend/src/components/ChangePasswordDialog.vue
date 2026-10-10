<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { request } from '../api/http'
import { useAuth } from '../stores/auth'
const visible = defineModel<boolean>({ default: false })
const oldPassword = ref('')
const newPassword = ref('')
const confirm = ref('')
const error = ref('')
const busy = ref(false)
const auth = useAuth()
const router = useRouter()
watch(visible, () => { oldPassword.value = ''; newPassword.value = ''; confirm.value = ''; error.value = '' })
async function save() {
  error.value = ''
  if (!oldPassword.value || !newPassword.value) { error.value = '请填写原密码和新密码'; return }
  if ([oldPassword.value, newPassword.value].some(value => new TextEncoder().encode(value).length > 72)) { error.value = '密码不能超过 72 个 UTF-8 字节'; return }
  if (newPassword.value !== confirm.value) { error.value = '两次新密码输入不一致'; return }
  busy.value = true
  try {
    await request('/auth/password', 'PUT', { oldPassword: oldPassword.value, newPassword: newPassword.value }, false)
    auth.clear(); visible.value = false
    ElMessage.success('密码已修改，请使用新密码重新登录')
    await router.replace('/login')
  } catch (e) { error.value = e instanceof Error ? e.message : '修改失败' }
  finally { busy.value = false; oldPassword.value = ''; newPassword.value = ''; confirm.value = '' }
}
</script>
<template><el-dialog v-model="visible" title="修改本人密码" width="440px" :close-on-click-modal="!busy" :show-close="!busy" :close-on-press-escape="!busy">
  <p class="muted">成功后，你在所有设备上的登录都会失效。</p><el-form label-position="top" @submit.prevent="save">
    <el-form-item label="原密码" for="changepassworddialog-oldPassword"><el-input id="changepassworddialog-oldPassword" v-model="oldPassword" type="password" autocomplete="current-password" show-password /></el-form-item>
    <el-form-item label="新密码" for="changepassworddialog-newPassword"><el-input id="changepassworddialog-newPassword" v-model="newPassword" type="password" autocomplete="new-password" show-password /></el-form-item>
    <el-form-item label="确认新密码" for="changepassworddialog-confirm"><el-input id="changepassworddialog-confirm" v-model="confirm" type="password" autocomplete="new-password" show-password /></el-form-item>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
  </el-form><template #footer><el-button :disabled="busy" @click="visible = false">取消</el-button><el-button type="primary" :loading="busy" @click="save">确认修改</el-button></template>
</el-dialog></template>
