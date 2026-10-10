<script setup lang="ts">
import { computed } from 'vue'
import { ElTree, ElIcon } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { House, Grid, Folder } from '@element-plus/icons-vue'
import { useAuth } from '../stores/auth'
import type { Menu } from '../api/types'
import { pageInfo } from '../ui/navigation'
defineProps<{ collapsed?: boolean }>()
const auth = useAuth()
const route = useRoute()
const router = useRouter()
const menus = computed<Menu[]>(() => [{ code: 'home', name: '工作台', routePath: '/', children: [] }, ...(auth.identity?.menus ?? [])])
const currentKey = computed(() => route.path === '/' ? 'home' : auth.identity?.menus.flatMap(group => group.children).find(item => item.routePath === route.path)?.code)
function navigate(node: Menu) { if (node.routePath) void router.push(node.routePath) }
</script>
<template>
  <router-link class="brand" to="/" aria-label="ManagentCenter 返回工作台"><span class="brand-mark"><Grid aria-hidden="true" /></span><span v-if="!collapsed" class="brand-copy">ManagentCenter</span></router-link>
  <el-tree :data="menus" node-key="code" :props="{ label: 'name', children: 'children' }" :current-node-key="currentKey" :indent="collapsed ? 0 : 12" highlight-current default-expand-all class="navigation-tree" aria-label="管理导航" @node-click="navigate">
    <template #default="{ data }"><span class="navigation-node" :title="data.name"><el-icon><component :is="data.children.length ? Folder : pageInfo[data.routePath as keyof typeof pageInfo]?.icon || House" /></el-icon><span v-if="!collapsed">{{ data.name }}</span></span></template>
  </el-tree>
</template>
