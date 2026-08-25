import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import formCreate from '@form-create/element-ui'
import FcDesigner from '@form-create/designer'
import App from './App.vue'
import router from './router'
import './styles/main.scss'
import TicketUserSelect from '@/components/ticket/TicketUserSelect.vue'
import TicketUsersSelect from '@/components/ticket/TicketUsersSelect.vue'
import TicketFileUpload from '@/components/ticket/TicketFileUpload.vue'

const app = createApp(App)
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

formCreate.component('TicketUserSelect', TicketUserSelect)
formCreate.component('TicketUsersSelect', TicketUsersSelect)
formCreate.component('TicketFileUpload', TicketFileUpload)
FcDesigner.component('TicketUserSelect', TicketUserSelect)
FcDesigner.component('TicketUsersSelect', TicketUsersSelect)
FcDesigner.component('TicketFileUpload', TicketFileUpload)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.use(formCreate)
app.use(FcDesigner)
app.use(FcDesigner.formCreate)
app.mount('#app')
