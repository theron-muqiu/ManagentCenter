import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import AdminLayout from './AdminLayout.vue'
import { useAuth } from '../stores/auth'

it('移动导航使用组件抽屉，切换页面后关闭并聚焦内容', async () => {
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
  setActivePinia(createPinia())
  useAuth().identity = { user: { username: 'Reader', displayName: '查看者' } as never, roleIds: [], permissionCodes: [], menus: [{ code: 'system', name: '系统管理', routePath: null, children: [{ code: 'users', name: '用户管理', routePath: '/system/users', children: [] }] }], expiresAt: '2099-01-01T00:00:00Z' }
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<p>工作台</p>' } }, { path: '/system/users', component: { template: '<p>用户管理</p>' } }] })
  await router.push('/')
  const wrapper = mount(AdminLayout, { attachTo: document.body, global: { plugins: [router, ElementPlus], stubs: { ChangePasswordDialog: true } } })
  await wrapper.get('.mobile-toggle').trigger('click')
  await flushPromises()
  const drawer = wrapper.findComponent({ name: 'ElDrawer' })
  expect(drawer.props('modelValue')).toBe(true)
  expect(drawer.props('closeOnPressEscape')).toBe(true)
  expect(drawer.props('lockScroll')).toBe(true)
  const tree = wrapper.findComponent({ name: 'ElTree' })
  const group = tree.findAll('[role=treeitem]')[1]!
  expect(group.attributes('aria-expanded')).toBe('true')
  await group.find('.el-tree-node__expand-icon').trigger('click')
  expect(group.attributes('aria-expanded')).toBe('false')
  await group.find('.el-tree-node__expand-icon').trigger('click')
  expect(group.attributes('aria-expanded')).toBe('true')
  await tree.findAll('.el-tree-node__content')[2]!.trigger('click')
  await flushPromises()
  expect(drawer.props('modelValue')).toBe(false)
  expect(document.activeElement).toBe(wrapper.get('main').element)
  drawer.vm.$emit('closed')
  await flushPromises()
  expect(document.activeElement).toBe(wrapper.get('main').element)
  wrapper.unmount()
  vi.unstubAllGlobals()
})
