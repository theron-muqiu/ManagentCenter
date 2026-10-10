import { mount, flushPromises } from '@vue/test-utils'
import { it, expect } from 'vitest'
import PermissionTree from './PermissionTree.vue'
import type { PermissionNode } from '../api/types'
it('勾选父节点仅改变父节点，不隐式选择子页面或操作', async () => {
  const child: PermissionNode = { id: '2', code: 'user:query', name: '用户管理', type: 1, parentId: '1', routePath: '/system/users', sortOrder: 1, children: [] }
  const node: PermissionNode = { id: '1', code: 'menu:system', name: '系统管理', type: 0, parentId: null, routePath: null, sortOrder: 0, children: [child] }
  const wrapper = mount(PermissionTree, { props: { nodes: [node], selected: [], disabled: false } })
  await flushPromises()
  const root = wrapper.find('[role=treeitem]')
  expect(root.attributes('aria-expanded')).toBe('true')
  await root.find('.el-tree-node__expand-icon').trigger('click')
  expect(root.attributes('aria-expanded')).toBe('false')
  expect(wrapper.emitted('update')).toBeUndefined()
  await root.find('.el-tree-node__expand-icon').trigger('click')
  expect(root.attributes('aria-expanded')).toBe('true')
  await wrapper.findAll('input')[0]!.setValue(true)
  expect(wrapper.emitted('update')?.[0]).toEqual([['1']])
  await wrapper.setProps({ selected: ['2'] })
  await flushPromises()
  await wrapper.findAll('input')[1]!.setValue(false)
  expect(wrapper.emitted('update')?.[1]).toEqual([[]])
  wrapper.unmount()
})
