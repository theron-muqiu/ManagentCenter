import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { beforeEach, afterEach, it, expect, vi } from 'vitest'
import ChangePasswordDialog from './ChangePasswordDialog.vue'
import { setToken, setBlocked, getToken } from '../api/http'
const replace = vi.fn()
vi.mock('vue-router', () => ({ useRouter: () => ({ replace }) }))
beforeEach(() => { setActivePinia(createPinia()); setToken('fixture'); setBlocked(false); replace.mockReset() })
afterEach(() => { vi.unstubAllGlobals(); setToken(null); document.body.innerHTML = '' })
it('改密成功清除当前凭据并返回登录页', async () => {
  const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 0, data: null })))
  vi.stubGlobal('fetch', fetch)
  const wrapper = mount(ChangePasswordDialog, { props: { modelValue: true }, global: { plugins: [ElementPlus], stubs: { teleport: true } } })
  await flushPromises()
  const inputs = wrapper.findAll('input')
  await inputs[0]!.setValue('old-fixture')
  await inputs[1]!.setValue('new-fixture')
  await inputs[2]!.setValue('new-fixture')
  const button = wrapper.findAll('button').find(item => item.text() === '确认修改')!
  await button.trigger('click')
  await flushPromises()
  expect(fetch).toHaveBeenCalledTimes(1)
  expect(getToken()).toBeNull()
  expect(replace).toHaveBeenCalledWith('/login')
  wrapper.unmount()
})
it('原密码错误保留登录状态，不跳转', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 40002, message: '原密码错误' }), { status: 400 })))
  const wrapper = mount(ChangePasswordDialog, { props: { modelValue: true }, global: { plugins: [ElementPlus], stubs: { teleport: true } } })
  await flushPromises()
  const inputs = wrapper.findAll('input')
  await inputs[0]!.setValue('wrong-fixture')
  await inputs[1]!.setValue('new-fixture')
  await inputs[2]!.setValue('new-fixture')
  await wrapper.findAll('button').find(item => item.text() === '确认修改')!.trigger('click')
  await flushPromises()
  expect(wrapper.find('[role="alert"]').text()).toBe('原密码错误')
  expect(getToken()).toBe('fixture')
  expect(replace).not.toHaveBeenCalled()
  wrapper.unmount()
})
