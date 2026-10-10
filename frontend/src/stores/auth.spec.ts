import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, afterEach, it, expect, vi } from 'vitest'
import { useAuth } from './auth'
import { setToken, getToken, setBlocked } from '../api/http'

beforeEach(() => { setActivePinia(createPinia()); setToken(null); setBlocked(false) })
afterEach(() => { vi.unstubAllGlobals(); setToken(null) })
it('重新加载从 sessionStorage 恢复身份，权限撤销后的刷新立即生效', async () => {
  setToken('test-credential')
  const identity = { user: { id: '1' }, roleIds: [], permissionCodes: ['user:query'], menus: [], expiresAt: '2099-01-01T00:00:00Z' }
  const fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ code: 0, data: identity })))
    .mockResolvedValueOnce(new Response(JSON.stringify({ code: 0, data: { ...identity, permissionCodes: [] } })))
  vi.stubGlobal('fetch', fetch)
  const auth = useAuth()
  await auth.refresh()
  expect(auth.can('user:query')).toBe(true)
  expect(fetch.mock.calls[0]?.[1].credentials).toBe('omit')
  await auth.refresh()
  expect(auth.can('user:query')).toBe(false)
})
it('认证依赖不可用时撤下身份，401 时清除凭据', async () => {
  setToken('test-credential')
  vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ code: 50301, message: '认证服务暂时不可用' }), { status: 503 }))
    .mockResolvedValueOnce(new Response(JSON.stringify({ code: 40101, message: '登录失效' }), { status: 401 })))
  const auth = useAuth()
  await auth.refresh()
  expect(auth.identity).toBeNull()
  expect(auth.unavailable).toBe(true)
  await auth.refresh()
  expect(getToken()).toBeNull()
  expect(auth.unavailable).toBe(false)
})
it('退出后的延迟身份响应不能恢复已清除的身份', async () => {
  setToken('test-credential')
  let complete!: (response: Response) => void
  vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise<Response>(resolve => { complete = resolve })))
  const auth = useAuth()
  const pending = auth.refresh()
  auth.clear()
  complete(new Response(JSON.stringify({ code: 0, data: { user: { id: '1' }, permissionCodes: ['user:query'] } })))
  await pending
  expect(auth.identity).toBeNull()
  expect(getToken()).toBeNull()
})
