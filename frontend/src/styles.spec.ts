import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect, it } from 'vitest'

function rules() {
  const style = document.createElement('style')
  style.textContent = readFileSync(resolve(process.cwd(), 'src/styles.css'), 'utf8')
  document.head.append(style)
  expect(style.sheet).not.toBeNull()
  const result: CSSStyleRule[] = []
  function collect(list: CSSRuleList) {
    for (const rule of Array.from(list)) {
      if ('selectorText' in rule) result.push(rule as CSSStyleRule)
      if ('cssRules' in rule) collect((rule as CSSMediaRule).cssRules)
    }
  }
  collect(style.sheet!.cssRules)
  style.remove()
  return result
}

it('页面样式不通过原生控件选择器覆盖组件内部元素', () => {
  const collisions = rules().filter(rule => rule.selectorText.split(',').some(selector =>
    /(^|[\s>+~])(input|textarea|select|button|label)(?=[\s.#:[>+~]|$)/.test(selector)
    || /^(a|svg|h[1-6]|p)(?=[\s.#:[>+~]|$)/.test(selector.trim()),
  ))
  expect(collisions.map(rule => rule.selectorText)).toEqual([])
})

it('组件样式只允许布局约束，导航树主题单独限定', () => {
  const layout = new Set(['width', 'max-width', 'margin', 'gap', 'flex-wrap'])
  const collisions: string[] = []
  for (const rule of rules()) {
    if (rule.selectorText.startsWith('.navigation-tree')) continue
    if (!rule.selectorText.includes('.el-')) continue
    for (let index = 0; index < rule.style.length; index++) {
      const property = rule.style[index]!
      if (!layout.has(property)) collisions.push(`${rule.selectorText}: ${property}`)
    }
  }
  expect(collisions).toEqual([])
})
