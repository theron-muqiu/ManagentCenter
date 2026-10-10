import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import LoginView from './LoginView.vue'
import { setToken } from '../api/http'
import { useAuth } from '../stores/auth'

const push = vi.fn()
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
describe('登录入口', () => {
  beforeEach(() => { setActivePinia(createPinia()); setToken(null); push.mockReset() })
  afterEach(() => vi.restoreAllMocks())
  it('拒绝空白账号和超字节密码，不提交无效凭据', async () => {
    const auth = useAuth()
    const login = vi.spyOn(auth, 'login').mockResolvedValue()
    const wrapper = mount(LoginView, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    await wrapper.find('#username').setValue(' Admin ')
    await wrapper.find('#password').setValue('example')
    await wrapper.find('form').trigger('submit')
    expect(wrapper.find('[role="alert"]').text()).toContain('首尾不能有空白')
    await wrapper.find('#username').setValue('Admin')
    await wrapper.find('#password').setValue('密'.repeat(25))
    await wrapper.find('form').trigger('submit')
    expect(wrapper.find('[role="alert"]').text()).toContain('72')
    expect(login).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  it('失败保持在登录页并清空密码，展示服务返回的安全说明', async () => {
    const auth = useAuth()
    vi.spyOn(auth, 'login').mockRejectedValue(new Error('账号或密码错误'))
    const wrapper = mount(LoginView, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    await wrapper.find('#username').setValue('Admin')
    await wrapper.find('#password').setValue('example')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').text()).toBe('账号或密码错误')
    expect((wrapper.find('#password').element as HTMLInputElement).value).toBe('')
    expect(push).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
