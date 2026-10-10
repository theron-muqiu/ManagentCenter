import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import HomeView from './HomeView.vue'
import { useAuth } from '../stores/auth'
import { request } from '../api/http'

vi.mock('../api/http', async original => ({ ...await original<typeof import('../api/http')>(), request: vi.fn() }))
beforeEach(() => {
  setActivePinia(createPinia())
  vi.mocked(request).mockReset()
  useAuth().identity = { user: { username: 'Reader', displayName: '查看者', roles: [] } as never, roleIds: [], permissionCodes: [], menus: [], expiresAt: '2099-01-01T00:00:00Z' }
})
function render() {
  return mount(HomeView, { global: { plugins: [ElementPlus], stubs: { RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' }, ElButton: { template: '<button><slot /></button>' } } } })
}
it('只读取有权限的数据，真实零值与读取失败分别展示', async () => {
  useAuth().identity!.permissionCodes = ['user:query', 'role:query']
  vi.mocked(request).mockImplementation(async path => {
    if (path.startsWith('/users')) return { total: 0, items: [] } as never
    throw new Error('服务暂不可用')
  })
  const wrapper = render()
  await flushPromises()
  expect(vi.mocked(request).mock.calls.map(call => call[0])).toEqual(['/users?page=1&size=1', '/roles?page=1&size=1'])
  const cards = wrapper.findAll('.metric-value')
  expect(cards.map(card => card.text())).toEqual(['0', '暂时无法读取', '无查看权限', '无查看权限'])
  expect(wrapper.text()).toContain('暂无日志查看权限')
  wrapper.unmount()
})
it('组件选项卡切换活动且撤销权限后隐藏原记录', async () => {
  useAuth().identity!.permissionCodes = ['operation-log:query', 'login-log:query']
  vi.mocked(request).mockResolvedValue({ total: 1, items: [{ id: '1', feature: '编辑用户', operatorUsername: 'Admin', attemptedUsername: 'Admin', occurredAt: '2026-10-10T01:00:00Z', result: 1 }] })
  const wrapper = render()
  await flushPromises()
  expect(wrapper.text()).toContain('编辑用户')
  await wrapper.get('#tab-logins').trigger('click')
  expect(wrapper.get('#tab-logins').attributes('aria-selected')).toBe('true')
  expect(wrapper.get('#tab-logins').attributes('tabindex')).toBe('0')
  useAuth().identity!.permissionCodes = []
  await flushPromises()
  expect(wrapper.find('[role="tab"]').exists()).toBe(false)
  expect(wrapper.text()).not.toContain('编辑用户')
  wrapper.unmount()
})
