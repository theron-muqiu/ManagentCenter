<script setup lang="ts">
import { ElResult } from 'element-plus'
</script>
<template><section class="status-page"><el-result icon="warning" title="访问受限" sub-title="请联系管理员确认角色授权"><template #extra><el-button type="primary" @click="$router.push('/')">返回工作台</el-button></template></el-result></section></template>
