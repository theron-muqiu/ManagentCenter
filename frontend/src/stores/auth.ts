import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { request, getToken, setToken, setBlocked, registerRefresh } from '../api/http'
import type { Identity, Login } from '../api/types'

export const useAuth = defineStore('auth', () => {
  const identity = ref<Identity | null>(null)
  const unavailable = ref(false)
  let pending: Promise<Identity | null> | null = null
  let generation = 0
  const permissions = computed(() => new Set(identity.value?.permissionCodes ?? []))
  function can(code: string) { return permissions.value.has(code) }
  function clear() { generation++; pending = null; identity.value = null; unavailable.value = false; setToken(null); setBlocked(false) }
  async function refresh(): Promise<Identity | null> {
    if (!getToken()) { identity.value = null; return null }
    if (pending) return pending
    const currentGeneration = generation
    const currentToken = getToken()
    const operation = request<Identity>('/auth/me', 'GET', undefined, false)
      .then(data => {
        if (generation !== currentGeneration || getToken() !== currentToken) return null
        identity.value = data; unavailable.value = false; setBlocked(false); return data
      })
      .catch(() => {
        if (generation === currentGeneration) { identity.value = null; unavailable.value = !!getToken(); setBlocked(unavailable.value) }
        return null
      })
      .finally(() => { if (pending === operation) pending = null })
    pending = operation
    return operation
  }
  async function login(username: string, password: string) {
    const data = await request<Login>('/auth/login', 'POST', { username, password }, false)
    generation++; pending = null
    setToken(data.token)
    await refresh()
  }
  async function logout() { await request('/auth/logout', 'POST', undefined, false); clear() }
  registerRefresh(refresh)
  window.addEventListener('auth-expired', clear)
  window.addEventListener('auth-unavailable', () => { identity.value = null; unavailable.value = true })
  return { identity, unavailable, can, clear, refresh, login, logout }
})
