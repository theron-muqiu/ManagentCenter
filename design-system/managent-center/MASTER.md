# ManagentCenter SaaS 基础平台 · 界面设计规范

范围：登录、工作台、用户/角色管理、授权弹窗、操作/登录日志、受限/故障页。保留既有 Vue 3 / Element Plus 和后端契约，不增加多租户、账单等未实现业务。

技能依据：ui-ux-pro-max。检索采用 `SaaS admin management dashboard`，随后用 `enterprise admin minimal professional` 收窄。检索的页面模式均偏营销，不作为后台页面蓝图；采用匹配的高对比、键盘导航、清晰表单、专业海军蓝及蓝色操作色建议。Vue 栈检索的语义元素和导航守卫规则适用；不为小型表单额外引入验证库。

## 视觉方向

浅色工作区 + 深海军蓝导航，蓝色作为唯一主要操作色。减少装饰，使用清晰文字、线性图标和细边框区分层次。桌面侧栏，移动端模态抽屉；中文界面，保持 ManagentCenter 产品名。

| Token | 值 | 用途 |
|---|---|---|
| canvas | #F5F7FA | 工作区 |
| surface | #FFFFFF | 卡片与弹窗 |
| text | #172033 | 正文 |
| muted | #5F6C80 | 次级文字 |
| primary | #245DE8 | 按钮、当前导航和焦点 |
| primary soft | #EDF3FF | 轻量强调 |
| sidebar | #151D2E | 导航背景 |
| sidebar text | #B8C3D6 | 次级导航 |
| border | #E3E8F0 | 卡片边界 |
| control border | #AAB6C7 | 输入控件边界 |
| success | #167454 | 成功，始终配文字 |
| danger | #B93838 | 错误/危险操作，配文字 |
| radius | 8 / 12 / 16 px | 控件 / 卡片 / 重点区域 |
| spacing | 4 / 8 / 12 / 16 / 24 / 32 / 48 px | 间距体系 |

字体：Inter（本机有则使用）、Segoe UI、PingFang SC、Microsoft YaHei、系统 sans-serif，无外部字体请求。正文14–16px、表格14px、标题28px；小文字不低于12px。数字使用等宽数字。

## 信息架构与交互

- 导航使用后端菜单，仅展示已授权入口；工作台始终可达。
- 工作台展示按独立查询权限读取的真实总数与最近日志。加载/失败/无权限分开表达，不填充假数据、不伪造健康状态或趋势。
- 列表统一页标题、说明、主操作、筛选栏、表头/行操作、结果条数与分页。
- 必填字段与可选字段清楚区分，表单保留密码管理器和粘贴；错误可被辅助技术读取。
- 移动端抽屉支持Escape关闭、焦点限制/归还；导航跳转后聚焦主内容，提供跳到主内容链接。
- 窄屏仅表格容器横向滚动，页面本身不横向溢出；弹窗宽度不超过视口。
- 焦点环可见；操作命中区域优先44px，辅助图标aria-hidden；不只靠颜色表达状态。
- 微交互使用短颜色/透明度过渡，尊重prefers-reduced-motion，不改变悬停布局。

验证范围：前端组件测试、类型/生产构建、静态响应式与对比度核对；沿用项目不运行端到端测试的约束。未执行浏览器验证的项目不得标为通过。

## 紧凑版调整

根据用户反馈，移除工作台宣传横幅、登录宣传语、列表重复描述及侧栏说明。桌面侧栏220px、顶栏56px、页面内边距20px/24px、表格单元格内边距10px/16px、主要控件32px；移动端保持44px操作区域。登录表单宽340px，保留账号大小写和错误提示等必要信息。

## Element Plus 优先原则

按用户最新要求，统一使用 Element Plus 现有组件：ElConfigProvider（中文、默认尺寸）、ElTable / ElTableColumn、ElPagination、ElSelect / ElOption、ElDatePicker、ElMenu、ElDrawer、ElDropdown、ElAvatar、ElBreadcrumb、ElTabs、ElCheckbox / ElCheckboxGroup 和 ElResult。组件在所属页面按需导入，保留布局与颜色变量，删除被组件替代的手写交互和样式。权限勾选仍独立，不自动关联父子节点。

组件测试保留真实表格、日期选择器、菜单、抽屉与复选框。ElPagination及页面ElSelect在jsdom下出现递归更新，业务测试仅对这两类组件使用测试替身，通过组件事件验证分页/筛选参数；不以此宣称浏览器交互已经验收。

## 默认控件外观与树形结构

按用户最新反馈，移除控件高度、圆角、颜色、字体、内边距等外观覆盖及全局Element Plus主题变量；采用默认尺寸配置、页面布局和弹窗视口宽度限制。菜单改用ElTree，支持分组展开/收起、当前页面高亮和叶节点路由。权限改用ElTree，show-checkbox和check-strictly启用独立选择，展开收起不影响授权；保存期间禁止修改。原递归权限行组件删除。

## 全量样式审查

检查全部前端样式和控件模板，删除遗留.date-filters input及媒体查询中的高度覆盖、全局button/input/select字体及交互规则、宽泛标签样式。登录标签使用专属class，日期筛选使用ElForm/ElFormItem组织标签和动作，避免命中组件内部元素。全局字体改为Element Plus官方字体变量。控件内部只保留布局限制，最近活动Tab栏左右16px留白；导航树主题独立限定。

styles.spec.ts解析全部应用CSS规则（含媒体查询），禁止通过原生控件标签覆盖内部元素，并限制El组件覆盖为布局属性。验证为组件/样式规则测试和生产构建，未运行浏览器端到端或实际设备视觉测试。

当前控件统一采用 Element Plus 默认尺寸，移除全局与局部 small 设置。

## 正文字号

正文及组件基础字体16px，辅助文字14px；通过官方--el-font-size-base/small/extra-small字号变量设置，保留控件默认尺寸、边框、圆角与焦点样式。
