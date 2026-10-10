import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { beforeEach, it, expect, vi } from 'vitest'
import UserView from './UserView.vue'
import { useAuth } from '../stores/auth'
import { request } from '../api/http'
import type { User } from '../api/types'
vi.mock('vue-router', () => ({ useRouter: () => ({ replace: vi.fn() }) }))
vi.mock('../api/http', async original => ({ ...await original<typeof import('../api/http')>(), request: vi.fn() }))
const user: User = { id: '2', username: 'Reader', displayName: '查看用户', phone: null, email: null, status: 1, superAdmin: false, version: '4', roles: [], createdAt: '2026-10-09T08:00:00Z', updatedAt: '2026-10-09T08:00:00Z' }
beforeEach(() => { setActivePinia(createPinia()); vi.mocked(request).mockReset(); vi.mocked(request).mockResolvedValue({ items: [user], total: 1, page: 1, size: 20 }) })
it('只有查询权时没有新增、编辑、状态调整或重置入口', async () => {
  useAuth().identity = { user, roleIds: [], permissionCodes: ['user:query'], menus: [], expiresAt: '2099-01-01T00:00:00Z' }
  const wrapper = mount(UserView, { global: { plugins: [ElementPlus], stubs: { teleport: true, ElPagination: true, ElSelect: true } } })
  await flushPromises()
  expect(wrapper.text()).toContain('Reader')
  const actions = wrapper.findAll('button').map(button => button.text())
  expect(actions).toContain('详情')
  expect(actions).not.toContain('新增用户')
  expect(actions).not.toContain('编辑')
  expect(actions).not.toContain('停用')
  expect(actions).not.toContain('重置密码')
  wrapper.unmount()
})
it('编辑仅提交可编辑字段和读取版本，不提交账号或密码', async () => {
  useAuth().identity = { user, roleIds: [], permissionCodes: ['user:query', 'user:edit'], menus: [], expiresAt: '2099-01-01T00:00:00Z' }
  const wrapper = mount(UserView, { global: { plugins: [ElementPlus], stubs: { teleport: true, ElPagination: true, ElSelect: true } } })
  await flushPromises()
  await wrapper.findAll('button').find(button => button.text() === '编辑')!.trigger('click')
  await flushPromises()
  expect(wrapper.findAll('input').some(input => (input.element as HTMLInputElement).value === 'Reader' && (input.element as HTMLInputElement).disabled)).toBe(true)
  await wrapper.findAll('button').find(button => button.text() === '保存')!.trigger('click')
  await flushPromises()
  expect(request).toHaveBeenCalledWith('/users/2', 'PUT', { version: '4', displayName: '查看用户', phone: null, email: null })
  wrapper.unmount()
})
it('组件分页和状态筛选使用当前值查询', async () => {
  useAuth().identity = { user, roleIds: [], permissionCodes: ['user:query'], menus: [], expiresAt: '2099-01-01T00:00:00Z' }
  const wrapper = mount(UserView, { global: { plugins: [ElementPlus], stubs: { teleport: true, ElPagination: true, ElSelect: true } } })
  await flushPromises()
  const pagination = wrapper.findComponent({ name: 'ElPagination' })
  expect(pagination.props('total')).toBe(1)
  wrapper.findComponent({ name: 'ElSelect' }).vm.$emit('update:modelValue', '0')
  pagination.vm.$emit('update:currentPage', 2)
  pagination.vm.$emit('update:pageSize', 10)
  await flushPromises()
  pagination.vm.$emit('change', 2, 10)
  await flushPromises()
  const params = new URLSearchParams(vi.mocked(request).mock.calls.at(-1)![0].split('?')[1])
  expect(params.get('page')).toBe('2')
  expect(params.get('size')).toBe('10')
  expect(params.get('status')).toBe('0')
  wrapper.unmount()
})
