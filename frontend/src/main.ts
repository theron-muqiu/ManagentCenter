import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElButton, ElDescriptions, ElDescriptionsItem, ElDialog, ElForm, ElFormItem, ElInput, ElTag, ElConfigProvider, ElLoading } from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import { router } from './router'
import './styles.css'

const app = createApp(App).use(createPinia()).use(router)
for (const component of [ElButton, ElDescriptions, ElDescriptionsItem, ElDialog, ElForm, ElFormItem, ElInput, ElTag, ElConfigProvider]) app.use(component)
app.use(ElLoading).mount('#app')
