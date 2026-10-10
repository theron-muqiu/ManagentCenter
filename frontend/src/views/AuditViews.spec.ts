import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, it, expect, vi } from 'vitest'
import OperationLogView from './OperationLogView.vue'
import LoginLogView from './LoginLogView.vue'
import { request } from '../api/http'
vi.mock('../api/http', async original => ({ ...await original<typeof import('../api/http')>(), request: vi.fn() }))
beforeEach(() => { vi.mocked(request).mockReset() })
it('操作记录显示安全文本，详情摘要不执行 HTML', async () => {
  const record = { id: '1', requestId: 'fixture-request', occurredAt: '2000-01-01T00:00:00Z', result: 0, errorCode: 40001, failureReason: '<img src=x onerror=alert(1)>', clientIp: '127.0.0.1', durationMs: 2, operatorUsername: 'Admin', operatorId: '2', operatorDisplayName: '管理员', module: 'user', feature: '编辑用户资料', operationType: 1, targetType: 0, targetId: '3' }
  vi.mocked(request).mockImplementation(async path => path.startsWith('/operation-logs?')
    ? { items: [record], total: 1, page: 1, size: 20 } as never
    : { ...record, requestMethod: 'PUT', requestPath: '/api/users/{id}', summary: { displayName: '<script>fixture</script>' }, userAgent: 'fixture-agent', createdAt: '2000-01-01T00:00:00Z' } as never)
  const wrapper = mount(OperationLogView, { global: { plugins: [ElementPlus], stubs: { teleport: true, ElPagination: true, ElSelect: true } } })
  await flushPromises()
  expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
  expect(wrapper.find('img').exists()).toBe(false)
  await wrapper.findAll('button').find(button => button.text() === '详情')!.trigger('click')
  await flushPromises()
  expect(wrapper.find('.audit-summary').text()).toContain('<script>fixture</script>')
  expect(wrapper.find('script').exists()).toBe(false)
  expect(wrapper.text()).toContain('fixture-request')
  wrapper.unmount()
})
it('登录日志筛选使用精确账号和 UTC 范围，反向时间不发起请求', async () => {
  vi.mocked(request).mockResolvedValue({ items: [], total: 0, page: 1, size: 20 })
  const wrapper = mount(LoginLogView, { global: { plugins: [ElementPlus], stubs: { teleport: true, ElPagination: true, ElSelect: true } } })
  await flushPromises()
  await wrapper.find('input[placeholder="尝试账号（精确匹配）"]').setValue('Reader')
  wrapper.findComponent({ name: 'ElSelect' }).vm.$emit('update:modelValue', '0')
  wrapper.findAllComponents({ name: 'ElDatePicker' })[0]!.vm.$emit('update:modelValue', '2000-01-01T12:00')
  await flushPromises()
  wrapper.findAllComponents({ name: 'ElDatePicker' })[1]!.vm.$emit('update:modelValue', '2000-01-01T13:00')
  await flushPromises()
  await wrapper.findAll('button').find(button => button.text() === '查询')!.trigger('click')
  await flushPromises()
  const path = vi.mocked(request).mock.calls.at(-1)![0]
  const params = new URLSearchParams(path.split('?')[1])
  expect(params.get('attemptedUsername')).toBe('Reader')
  expect(params.get('result')).toBe('0')
  expect(params.get('startTime')).toBe(new Date('2000-01-01T12:00').toISOString())
  expect(wrapper.text()).toContain('暂无登录日志')
  const count = vi.mocked(request).mock.calls.length
  wrapper.findAllComponents({ name: 'ElDatePicker' })[1]!.vm.$emit('update:modelValue', '2000-01-01T11:00')
  await flushPromises()
  await wrapper.findAll('button').find(button => button.text() === '查询')!.trigger('click')
  await flushPromises()
  expect(wrapper.find('[role="alert"]').text()).toContain('结束时间必须晚于开始时间')
  expect(request).toHaveBeenCalledTimes(count)
  wrapper.unmount()
})
