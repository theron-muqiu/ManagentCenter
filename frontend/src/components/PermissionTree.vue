<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { ElTree, ElTag } from 'element-plus'
import type { PermissionNode } from '../api/types'
const props = defineProps<{ nodes: PermissionNode[]; selected: string[]; disabled: boolean }>()
const emit = defineEmits<{ update: [ids: string[]] }>()
const tree = ref<InstanceType<typeof ElTree>>()
watch(() => [props.nodes, props.selected], async () => {
  await nextTick()
  tree.value?.setCheckedKeys(props.selected)
}, { immediate: true })
function check(_node: PermissionNode, state: { checkedKeys: (string | number)[] }) {
  emit('update', state.checkedKeys.map(String))
}
</script>
<template><el-tree ref="tree" :data="nodes" node-key="id" :props="{ label: 'name', children: 'children', disabled: () => props.disabled }" show-checkbox check-strictly default-expand-all :expand-on-click-node="true" :check-on-click-node="false" aria-label="角色权限" @check="check"><template #default="{ data }"><span class="permission-node">{{ data.name }}<el-tag type="info">{{ ['菜单', '页面', '操作'][data.type] }}</el-tag></span></template></el-tree></template>
