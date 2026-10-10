<script setup lang="ts">
import { ElResult } from 'element-plus'
import { useRouter } from 'vue-router'
const router = useRouter()
</script>
<template><section class="status-page"><el-result icon="error" title="服务暂不可用" sub-title="暂时无法确认登录状态，请稍后重试"><template #extra><el-button type="primary" @click="router.push('/')">重新检查</el-button></template></el-result></section></template>
