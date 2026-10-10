<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../stores/auth'
import { Grid, Lock, User, Key, ArrowRight, CircleCheck } from '@element-plus/icons-vue'
const auth = useAuth()
const router = useRouter()
const username = ref('')
const password = ref('')
const busy = ref(false)
const error = ref('')
const errorField = ref('')
async function submit() {
  error.value = ''
  errorField.value = ''
  if (!username.value || username.value !== username.value.trim() || username.value.length > 64) { error.value = '请填写账号，首尾不能有空白'; errorField.value = 'username'; await nextTick(); document.getElementById('username')?.focus(); return }
  if (!password.value || new TextEncoder().encode(password.value).length > 72) { error.value = '密码不能为空，且不能超过 72 个 UTF-8 字节'; errorField.value = 'password'; await nextTick(); document.getElementById('password')?.focus(); return }
  busy.value = true
  try { await auth.login(username.value, password.value); await router.push(auth.identity ? '/' : '/unavailable') }
  catch (e) { error.value = e instanceof Error ? e.message : '登录失败'; await nextTick(); document.getElementById('login-error')?.focus() }
  finally { busy.value = false; password.value = '' }
}
</script>
<template>
  <main class="login-page">
    <section class="login-intro">
      <div class="brand"><span class="brand-mark" aria-hidden="true"><Grid /></span><span>ManagentCenter<small>基础管理平台</small></span></div>
      <div class="login-story"><h1>ManagentCenter</h1>
        <div class="login-diagram" aria-hidden="true"><div class="diagram-rail"></div><div class="diagram-card diagram-user"><span class="diagram-icon"><User /></span><div><strong>成员账号</strong><small>统一管理 · 各司其职</small></div><CircleCheck /></div><div class="diagram-center"><Lock /></div><div class="diagram-card diagram-role"><span class="diagram-icon"><Key /></span><div><strong>角色与权限</strong><small>按职责授权 · 即时生效</small></div><CircleCheck /></div></div>
      </div><div class="login-intro-foot"><span>系统管理</span><i></i><span>访问控制</span><i></i><span>操作审计</span></div>
    </section>
    <section class="login-panel"><div class="login-panel-top"><span class="login-mobile-brand"><Grid aria-hidden="true" />ManagentCenter</span><span class="login-security"><Lock aria-hidden="true" />授权访问</span></div><div class="login-card"><h2>登录</h2>
      <form :aria-busy="busy" @submit.prevent="submit"><label class="login-field-label" for="username">账号<span class="required-mark">*</span></label><el-input id="username" v-model="username" autocomplete="username" maxlength="64" placeholder="请输入账号" :aria-invalid="errorField === 'username'" :aria-describedby="errorField === 'username' ? 'login-error' : 'username-help'" :prefix-icon="User" /><small id="username-help" class="field-hint">账号区分大小写</small>
        <label class="login-field-label" for="password">密码<span class="required-mark">*</span></label><el-input id="password" v-model="password" type="password" show-password autocomplete="current-password" placeholder="请输入密码" :aria-invalid="errorField === 'password'" :aria-describedby="errorField === 'password' ? 'login-error' : undefined" :prefix-icon="Lock" />
        <p v-if="error" id="login-error" class="form-error" role="alert" tabindex="-1">{{ error }}</p><el-button native-type="submit" type="primary" :loading="busy" class="full-width">{{ busy ? '正在登录…' : '登录' }}<ArrowRight v-if="!busy" aria-hidden="true" class="button-icon trailing" /></el-button>
      </form><div class="login-support"><span>账号问题？</span><strong>联系管理员</strong></div>
    </div><footer class="login-panel-footer">ManagentCenter · 基础管理平台</footer></section>
  </main>
</template>
