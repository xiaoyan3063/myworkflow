<template>
  <div class="designer-page">
    <div class="bar">
      <div>
        <h1 class="page-title">表单设计</h1>
        <p class="page-sub">{{ typeName }} · schema 存 tk_form_ui，保存后同步字段到 tk_field</p>
      </div>
      <div>
        <el-button @click="router.push('/ticket-types')">返回</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存设计</el-button>
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
import { dropEmptyOptions, ticketUserDragRule, ticketUsersDragRule } from '@/components/ticket/fcTicketRules'

const route = useRoute()
const router = useRouter()
const designer = ref<any>()
const saving = ref(false)
const typeName = ref('')
const typeId = route.params.id as string

// 字段 ID 就是 tk_field.field_key，必须允许业务改成稳定名
const designerConfig = { fieldReadonly: false }

onMounted(async () => {
  await nextTick()
  designer.value?.addComponent?.(ticketUserDragRule)
  designer.value?.addComponent?.(ticketUsersDragRule)
  const t: any = await http.get(`/ticket/types/${typeId}`)
  typeName.value = t.data?.typeName || ''
  const ui: any = await http.get(`/ticket/types/${typeId}/form-ui`)
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
    ElMessage.success('已保存表单设计')
  } finally {
    saving.value = false
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
