import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { beforeEach, it, expect, vi } from 'vitest'
import RoleView from './RoleView.vue'
import { useAuth } from '../stores/auth'
import { request } from '../api/http'
vi.mock('../api/http', async original => ({ ...await original<typeof import('../api/http')>(), request: vi.fn() }))
beforeEach(() => { setActivePinia(createPinia()); vi.mocked(request).mockReset(); vi.mocked(request).mockResolvedValue({ items: [{ id: '1', name: '查看者', version: '4', description: null, permissionCodes: ['user:query'] }], total: 1, page: 1, size: 20 }) })
it('查询权限仅显示角色详情，不显示修改、删除和授权', async () => {
  const auth = useAuth()
  auth.identity = { user: { id: '2', username: 'Reader' } as never, roleIds: [], permissionCodes: ['role:query'], menus: [], expiresAt: '2099-01-01T00:00:00Z' }
  const wrapper = mount(RoleView, { global: { plugins: [ElementPlus], stubs: { teleport: true, ElPagination: true } } })
  await flushPromises()
  expect(wrapper.text()).toContain('查看者')
  const buttons = wrapper.findAll('button').map(button => button.text())
  expect(buttons).toContain('详情')
  for (const action of ['新增角色', '编辑', '删除', '分配权限']) expect(buttons).not.toContain(action)
  wrapper.unmount()
})
