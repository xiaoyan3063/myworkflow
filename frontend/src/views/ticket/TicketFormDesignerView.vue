<template>
  <div class="designer-page">
    <div class="bar">
      <div>
        <h1 class="page-title">表单设计</h1>
        <p class="page-sub">{{ typeName }} · 保存为草稿，发布后运行时才生效</p>
      </div>
      <div>
        <el-tag v-if="uiStatus" size="small" :type="uiStatus === 'PUBLISHED' ? 'success' : 'info'" style="margin-right: 8px">
          {{ uiStatus === 'PUBLISHED' ? '已发布' : '草稿' }} v{{ uiVersion || 1 }}
        </el-tag>
        <el-button @click="router.push('/ticket-types')">返回</el-button>
        <el-button :loading="saving" @click="save">保存草稿</el-button>
        <el-button type="primary" :loading="publishing" @click="publish">发布</el-button>
      </div>
    </div>
    <div class="canvas">
      <fc-designer ref="designer" height="100%" :config="designerConfig" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/utils/http'
import { dropEmptyOptions, ticketFileDragRule, ticketUserDragRule, ticketUsersDragRule } from '@/components/ticket/fcTicketRules'

const route = useRoute()
const router = useRouter()
const designer = ref<any>()
const saving = ref(false)
const publishing = ref(false)
const typeName = ref('')
const uiStatus = ref('')
const uiVersion = ref(1)
const typeId = route.params.id as string

// 字段 ID 就是 tk_field.field_key，必须允许业务改成稳定名
const designerConfig = { fieldReadonly: false }

onMounted(async () => {
  await nextTick()
  designer.value?.addComponent?.(ticketUserDragRule)
  designer.value?.addComponent?.(ticketUsersDragRule)
  designer.value?.addComponent?.(ticketFileDragRule)
  const t: any = await http.get(`/ticket/types/${typeId}`)
  typeName.value = t.data?.typeName || ''
  const ui: any = await http.get(`/ticket/types/${typeId}/form-ui`)
  uiStatus.value = ui.data?.status || 'DRAFT'
  uiVersion.value = ui.data?.version || 1
  const raw = ui.data?.schema?.raw
  if (Array.isArray(raw) && raw.length && designer.value?.setRule) {
    designer.value.setRule(dropEmptyOptions(raw))
  }
})

async function save() {
  if (!designer.value) return
  saving.value = true
  try {
    const raw = designer.value.getRule ? designer.value.getRule() : []
    const cleaned = dropEmptyOptions(JSON.parse(JSON.stringify(raw)))
    await http.put(`/ticket/types/${typeId}/form-ui`, { raw: cleaned })
    ElMessage.success('已保存草稿')
    const ui: any = await http.get(`/ticket/types/${typeId}/form-ui`)
    uiStatus.value = ui.data?.status || 'DRAFT'
    uiVersion.value = ui.data?.version || 1
  } finally {
    saving.value = false
  }
}

async function publish() {
  publishing.value = true
  try {
    await save()
    const ui: any = await http.post(`/ticket/types/${typeId}/form-ui/publish`)
    uiStatus.value = ui.data?.status || 'PUBLISHED'
    uiVersion.value = ui.data?.version || uiVersion.value
    ElMessage.success('已发布，新建工单将使用此版本')
  } finally {
    publishing.value = false
  }
}
</script>

<style scoped>
.designer-page {
  height: calc(100vh - 88px);
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
}
.bar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 12px 16px;
  border-bottom: 1px solid #eef2f0;
}
.page-title { margin: 0; font-size: 18px; }
.page-sub { margin: 4px 0 0; color: #7a8a84; font-size: 13px; }
.canvas { flex: 1; min-height: 0; }
</style>
