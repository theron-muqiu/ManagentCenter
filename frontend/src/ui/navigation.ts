import { House, User, Key, Document, Unlock } from '@element-plus/icons-vue'

/** 页面说明与图标；实际导航入口仍以服务端授权菜单为准。 */
export const pageInfo = {
  '/': { name: '工作台', section: '概览', icon: House, description: '你的管理工作，从这里开始。' },
  '/system/users': { name: '用户管理', section: '系统管理', icon: User, description: '管理账号资料、登录状态与角色分配。' },
  '/system/roles': { name: '角色管理', section: '系统管理', icon: Key, description: '按职责组织权限，让每位成员各司其职。' },
  '/audit/operations': { name: '操作记录', section: '日志管理', icon: Document, description: '追溯关键变更，查看每次操作的处理结果。' },
  '/audit/logins': { name: '登录日志', section: '日志管理', icon: Unlock, description: '查看登录活动，及时了解账号访问情况。' },
  '/forbidden': { name: '访问受限', section: '工作台', icon: House, description: '' },
  '/unavailable': { name: '服务暂不可用', section: '工作台', icon: House, description: '' },
}
