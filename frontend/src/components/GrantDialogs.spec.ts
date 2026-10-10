import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { beforeEach, it, expect, vi } from 'vitest'
import UserRoleDialog from './UserRoleDialog.vue'
import RolePermissionDialog from './RolePermissionDialog.vue'
import { request } from '../api/http'
vi.mock('../api/http', async original => ({ ...await original<typeof import('../api/http')>(), request: vi.fn() }))
beforeEach(() => { setActivePinia(createPinia()); vi.mocked(request).mockReset() })
it('用户角色弹窗只读取授权接口，清空后提交完整空集合及版本', async () => {
  vi.mocked(request).mockImplementation(async path => {
    if (path === '/users/2/roles') return { userId: '2', roleIds: ['4'], version: '7' } as never
    if (path === '/role-options') return [{ id: '4', name: '查看者' }] as never
    return null as never
  })
  const wrapper = mount(UserRoleDialog, { props: { modelValue: true, userId: '2', username: 'Reader' }, global: { plugins: [ElementPlus], stubs: { teleport: true } } })
  await flushPromises()
  await wrapper.find('input[type="checkbox"]').setValue(false)
  await wrapper.findAll('button').find(button => button.text() === '保存授权')!.trigger('click')
  await flushPromises()
  expect(request).toHaveBeenCalledWith('/users/2/roles', 'PUT', { version: '7', roleIds: [] })
  expect(vi.mocked(request).mock.calls.some(([path]) => path === '/roles')).toBe(false)
  wrapper.unmount()
})
it('角色权限弹窗读取完整树，但提交的仅是显式选择的权限', async () => {
  vi.mocked(request).mockImplementation(async path => {
    if (path === '/roles/4/permissions') return { roleId: '4', permissionIds: [], version: '3' } as never
    if (path === '/permissions/tree') return [{ id: '1', name: '系统管理', code: 'menu:system', type: 0, children: [{ id: '2', name: '用户管理', code: 'user:query', type: 1, children: [] }] }] as never
    return null as never
  })
  const wrapper = mount(RolePermissionDialog, { props: { modelValue: true, roleId: '4', name: '查看者' }, global: { plugins: [ElementPlus], stubs: { teleport: true } } })
  await flushPromises()
  await wrapper.findAll('input[type="checkbox"]')[1]!.setValue(true)
  await wrapper.findAll('button').find(button => button.text() === '保存授权')!.trigger('click')
  await flushPromises()
  expect(request).toHaveBeenCalledWith('/roles/4/permissions', 'PUT', { version: '3', permissionIds: ['2'] })
  wrapper.unmount()
})
