import type { Identity } from './types'

const tokenKey = 'rbac.accessToken'
let token = sessionStorage.getItem(tokenKey)
let refresh: (() => Promise<Identity | null>) | undefined
let blocked = false
export class ApiError extends Error {
  constructor(public code: number, message: string) { super(message) }
}
export function setToken(value: string | null) {
  token = value
  if (value) sessionStorage.setItem(tokenKey, value)
  else sessionStorage.removeItem(tokenKey)
}
export function getToken() { return token }
export function setBlocked(value: boolean) { blocked = value }
export function registerRefresh(handler: () => Promise<Identity | null>) { refresh = handler }
export async function request<T>(path: string, method = 'GET', body?: unknown, syncIdentity = true): Promise<T> {
  if (blocked && path !== '/auth/me' && path !== '/auth/login') throw new ApiError(50301, '暂时无法确认登录状态，请先重新检查')
  let response: Response
  try {
    response = await fetch('/api' + path, { method, credentials: 'omit', headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
    }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) })
  } catch { throw new ApiError(50000, '连接失败，请稍后再试') }
  const data = await response.json().catch(() => ({ code: 50000, message: '服务暂时不可用' }))
  if (data.code === 40101) { setToken(null); window.dispatchEvent(new Event('auth-expired')) }
  if (data.code === 50301) { blocked = true; window.dispatchEvent(new Event('auth-unavailable')) }
  if (syncIdentity && token && (data.code === 0 || data.code === 40301)) await refresh?.()
  if (!response.ok || data.code !== 0) throw new ApiError(data.code, data.message)
  return data.data as T
}
