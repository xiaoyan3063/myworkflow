<template>
  <div class="page-card">
    <div class="head">
      <div>
        <h1 class="page-title">详情配置</h1>
        <p class="page-sub">{{ typeName }} · 区块 + 字段多选。保存为草稿，发布后运行时才用。</p>
      </div>
      <div>
        <el-tag v-if="uiStatus" size="small" :type="uiStatus === 'PUBLISHED' ? 'success' : 'info'">
          {{ uiStatus === 'PUBLISHED' ? '已发布' : '草稿' }} v{{ uiVersion || 1 }}
        </el-tag>
        <el-button @click="$router.push(`/tickets/${typeCode}`)" :disabled="!typeCode">打开列表</el-button>
        <el-button @click="$router.push('/ticket-types')">返回</el-button>
        <el-button :loading="saving" @click="save">保存草稿</el-button>
        <el-button type="primary" :loading="publishing" @click="publish">发布</el-button>
      </div>
    </div>

    <el-form inline class="opts">
      <el-form-item label="显示审批轨迹">
        <el-switch v-model="showTimeline" />
      </el-form-item>
      <el-form-item label="按钮">
        <el-checkbox-group v-model="actions">
          <el-checkbox label="save">保存</el-checkbox>
          <el-checkbox label="submit">提交审批</el-checkbox>
          <el-checkbox label="cancel">返回</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
    </el-form>

    <div class="sec-bar">
      <span>区块按从上到下渲染。主表字段只读展示（标题在草稿/已驳回时可改）；扩展字段复用 TicketForm。</span>
      <el-button size="small" @click="addSection">新增区块</el-button>
    </div>

    <el-card v-for="(sec, i) in sections" :key="i" class="sec" shadow="never">
      <div class="sec-head">
        <el-input v-model="sec.title" placeholder="区块标题" style="max-width: 280px" />
        <div>
          <el-button link :disabled="i === 0" @click="move(i, -1)">上移</el-button>
          <el-button link :disabled="i === sections.length - 1" @click="move(i, 1)">下移</el-button>
          <el-button link type="danger" :disabled="sections.length <= 1" @click="sections.splice(i, 1)">删除</el-button>
        </div>
      </div>
      <div class="group">
        <p class="group-title">主表</p>
        <el-checkbox-group v-model="sec.fields">
          <el-checkbox v-for="f in MAIN" :key="f.field" :label="f.field">{{ f.title }}</el-checkbox>
        </el-checkbox-group>
      </div>
      <div class="group" v-if="formFields.length">
        <p class="group-title">表单字段（tk_field）</p>
        <el-checkbox-group v-model="sec.fields">
          <el-checkbox v-for="f in formFields" :key="f.field" :label="f.field">{{ f.title }}</el-checkbox>
        </el-checkbox-group>
      </div>
      <p v-else class="empty">还没有表单字段，请先打开「设计表单」保存。</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/utils/http'

const MAIN = [
  { field: 'ticket_no', title: '工单号' },
  { field: 'title', title: '标题' },
  { field: 'status', title: '状态' },
  { field: 'typeName', title: '类型' },
  { field: 'starterName', title: '发起人' },
  { field: 'currentApprover', title: '当前审批人' },
  { field: 'processKey', title: '绑定流程' },
  { field: 'createTime', title: '创建时间' },
  { field: 'processInstId', title: '流程实例' },
]

const route = useRoute()
const typeId = route.params.id as string
const typeName = ref('')
const typeCode = ref('')
const saving = ref(false)
const publishing = ref(false)
const uiStatus = ref('')
const uiVersion = ref(1)
const showTimeline = ref(true)
const actions = ref<string[]>(['save', 'submit'])
const sections = ref<{ title: string; fields: string[] }[]>([])
const formFields = ref<{ field: string; title: string }[]>([])

function addSection() {
  sections.value.push({ title: '未命名区块', fields: [] })
}

function move(i: number, dir: number) {
  const j = i + dir
  const arr = [...sections.value]
  const tmp = arr[i]
  arr[i] = arr[j]
  arr[j] = tmp
  sections.value = arr
}

onMounted(async () => {
  const t: any = await http.get(`/ticket/types/${typeId}`)
  typeName.value = t.data?.typeName || ''
  typeCode.value = t.data?.typeCode || ''
  const [fieldsRes, uiRes]: any[] = await Promise.all([
    http.get(`/ticket/types/${typeId}/fields`),
    http.get(`/ticket/types/${typeId}/detail-ui`),
  ])
  formFields.value = (fieldsRes.data || []).map((f: any) => ({ field: f.fieldKey, title: f.title }))
  const schema = uiRes.data?.schema || {}
  uiStatus.value = uiRes.data?.status || 'DRAFT'
  uiVersion.value = uiRes.data?.version || 1
  showTimeline.value = schema.showTimeline !== false
  actions.value = Array.isArray(schema.actions) ? [...schema.actions] : ['save', 'submit']
  if (Array.isArray(schema.sections) && schema.sections.length) {
    sections.value = schema.sections.map((s: any) => ({
      title: s.title || '未命名区块',
      fields: [...(s.fields || [])],
    }))
  } else {
    sections.value = [{
      title: '基本信息',
      fields: ['ticket_no', 'title', 'status', 'starterName', 'createTime'],
    }]
    if (formFields.value.length) {
      sections.value.push({ title: '申请内容', fields: formFields.value.map((f) => f.field) })
    }
  }
})

async function save() {
  const payload = {
    showTimeline: showTimeline.value,
    actions: actions.value,
    sections: sections.value.map((s) => ({ title: s.title, fields: s.fields })),
  }
  if (!payload.sections.some((s) => s.fields.length)) {
    return ElMessage.warning('请至少在一个区块里勾选字段')
  }
  saving.value = true
  try {
    await http.put(`/ticket/types/${typeId}/detail-ui`, payload)
    ElMessage.success('已保存草稿')
    const ui: any = await http.get(`/ticket/types/${typeId}/detail-ui`)
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
    const ui: any = await http.post(`/ticket/types/${typeId}/detail-ui/publish`)
    uiStatus.value = ui.data?.status || 'PUBLISHED'
    uiVersion.value = ui.data?.version || uiVersion.value
    ElMessage.success('已发布详情配置')
  } finally {
    publishing.value = false
  }
}
</script>
<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.head > div:last-child { display: flex; gap: 8px; }
.page-title { margin: 0; }
.page-sub { margin: 4px 0 0; color: #7a8a84; }
.opts { margin-bottom: 8px; }
.sec-bar { display: flex; justify-content: space-between; align-items: center; margin: 8px 0 12px; color: #7a8a84; font-size: 13px; gap: 12px; }
.sec { margin-bottom: 12px; }
.sec-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.group { margin-top: 8px; }
.group-title { margin: 0 0 6px; font-size: 13px; color: #7a8a84; }
.empty { margin: 8px 0 0; color: #9aa7a2; font-size: 13px; }
:deep(.el-checkbox) { margin-right: 12px; margin-bottom: 6px; }
</style>
