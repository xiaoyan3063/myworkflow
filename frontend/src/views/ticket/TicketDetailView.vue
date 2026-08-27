<template>
  <div class="page-card" v-loading="loading">
    <div class="head">
      <div>
        <h1 class="page-title">工单详情</h1>
        <p class="page-sub">{{ ticket.ticketNo }} · {{ ticket.title }}</p>
      </div>
      <div class="head-actions">
        <el-button
          v-if="hasAction('save') && canEdit && hasPerm('ticket:update')"
          type="primary"
          :loading="saving"
          @click="save"
        >保存</el-button>
        <el-button
          v-if="hasAction('submit') && canSubmit && hasPerm('ticket:submit')"
          type="success"
          :loading="submitting"
          @click="submit"
        >提交审批</el-button>
        <ApprovalActions
          v-if="myTask"
          :task="myTask"
          :before-action="saveBeforeAction"
          @done="onActionDone"
        />
        <el-button v-if="hasAction('cancel')" @click="goBack">返回</el-button>
        <el-button v-else @click="goBack">返回列表</el-button>
      </div>
    </div>

    <el-alert
      v-if="myTask"
      class="task-tip"
      :type="myTask.resubmitTask ? 'warning' : 'info'"
      :closable="false"
      show-icon
      :title="myTask.resubmitTask
        ? '该工单已被退回，请修改后重新提交'
        : `当前节点【${myTask.taskName}】待您审批`"
    />

    <el-row v-if="!loading" :gutter="20">
      <el-col :span="showRight ? 14 : 24">
        <div v-for="(sec, i) in sections" :key="i" class="section">
          <h3>{{ sec.title }}</h3>
          <div v-if="mainOf(sec).length" class="info">
            <div v-for="f in mainOf(sec)" :key="f" class="info-cell" :class="{ wide: f === 'processInstId' || f === 'title' }">
              <span class="info-label">{{ mainTitle(f) }}</span>
              <span class="info-value">
                <el-input v-if="f === 'title' && canEdit && hasPerm('ticket:update')" v-model="ticket.title" size="small" />
                <el-tag v-else-if="f === 'status'" size="small" :type="ticketStatusTone(ticket.status)">
                  {{ ticketStatusText(ticket.status) }}
                </el-tag>
                <router-link v-else-if="f === 'processInstId' && ticket.processInstId" class="link" :to="`/instance/${ticket.processInstId}`">
                  {{ ticket.processInstId }}
                </router-link>
                <template v-else>{{ mainValue(f) || '-' }}</template>
              </span>
            </div>
          </div>
          <TicketForm
            v-if="formOf(sec).length"
            class="sec-form"
            :model-value="formData"
            :schema="formSchema"
            :only-fields="formOf(sec)"
            :disabled="!canEdit || !hasPerm('ticket:update')"
            @update:model-value="patchForm"
          />
        </div>
      </el-col>
      <el-col v-if="showRight" :span="10">
        <h3>审批轨迹</h3>
        <ApprovalTimeline v-if="hasTimeline" :data="timeline" />
        <el-empty v-else description="尚未提交审批" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/utils/http'
import TicketForm from '@/components/ticket/TicketForm.vue'
import ApprovalTimeline from '@/components/ApprovalTimeline.vue'
import ApprovalActions from '@/components/ApprovalActions.vue'
import { ticketStatusText, ticketStatusTone } from '@/utils/status'
import { hasPerm } from '@/utils/permission'

const MAIN_TITLE: Record<string, string> = {
  ticket_no: '工单号',
  title: '标题',
  status: '状态',
  typeName: '类型',
  starterName: '发起人',
  currentApprover: '当前审批人',
  processKey: '绑定流程',
  createTime: '创建时间',
  processInstId: '流程实例',
}

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const saving = ref(false)
const submitting = ref(false)
const ticket = ref<any>({})
const formData = ref<Record<string, any>>({})
const formSchema = ref<any>({ fields: [], raw: [] })
const timeline = ref<any>({ nodes: [] })
const myTask = ref<any>(null)
const detailSchema = ref<any>({
  showTimeline: true,
  sections: [],
  actions: ['save', 'submit'],
})

const isDraftLike = computed(() => ticket.value.status === 'DRAFT' || ticket.value.status === 'REJECTED')
// 被退回到发起人节点时工单仍是审批中，但持有重提待办的人要能改表单
const canEdit = computed(() => isDraftLike.value || !!myTask.value?.resubmitTask)
const canSubmit = computed(() => isDraftLike.value)
const sections = computed(() => detailSchema.value.sections || [])
const hasTimeline = computed(() => !!(timeline.value?.startTime || timeline.value?.nodes?.length))
const showRight = computed(() => detailSchema.value.showTimeline !== false)

function hasAction(name: string) {
  const acts = detailSchema.value.actions
  if (!Array.isArray(acts) || !acts.length) return name !== 'cancel'
  return acts.includes(name)
}

function mainOf(sec: any): string[] {
  return (sec.fields || []).filter((f: string) => MAIN_TITLE[f])
}

function formOf(sec: any): string[] {
  return (sec.fields || []).filter((f: string) => !MAIN_TITLE[f])
}

function mainTitle(f: string) {
  return MAIN_TITLE[f] || f
}

function mainValue(f: string) {
  const t = ticket.value || {}
  if (f === 'ticket_no') return t.ticketNo
  if (f === 'createTime') return t.createTime
  if (f === 'processInstId') return t.processInstId
  return t[f]
}

function patchForm(partial: Record<string, any>) {
  formData.value = { ...formData.value, ...partial }
}

function goBack() {
  const code = (route.params.typeCode as string) || ticket.value.typeCode
  router.push(code ? `/tickets/${code}` : '/tickets')
}

async function save() {
  saving.value = true
  try {
    await http.put(`/ticket/tickets/${ticket.value.id}`, {
      title: ticket.value.title,
      formData: { ...formData.value },
    })
    ElMessage.success('已保存')
  } finally {
    saving.value = false
  }
}

async function submit() {
  await ElMessageBox.confirm(`提交工单「${ticket.value.ticketNo}」进入审批？`, '确认')
  submitting.value = true
  try {
    if (canEdit.value) {
      await http.put(`/ticket/tickets/${ticket.value.id}`, {
        title: ticket.value.title,
        formData: { ...formData.value },
      })
    }
    const res: any = await http.post(`/ticket/tickets/${ticket.value.id}/submit`)
    ticket.value = res.data || ticket.value
    ElMessage.success('已提交审批')
    await reload()
  } finally {
    submitting.value = false
  }
}

async function loadMyTask() {
  myTask.value = null
  if (!ticket.value.processInstId) return
  try {
    const res: any = await http.get(`/runtime/instances/${ticket.value.processInstId}/my-task`)
    myTask.value = res.data || null
  } catch {
    myTask.value = null
  }
}

/** 重新提交前先把改过的表单落库，否则审批人看到的还是退回前的内容 */
async function saveBeforeAction() {
  if (!myTask.value?.resubmitTask || !hasPerm('ticket:update')) return
  await http.put(`/ticket/tickets/${ticket.value.id}`, {
    title: ticket.value.title,
    formData: { ...formData.value },
  })
}

async function onActionDone() {
  await reload()
}

async function reload() {
  const id = route.params.id as string
  const res: any = await http.get(`/ticket/tickets/${id}`)
  ticket.value = res.data || {}
  formData.value = { ...(ticket.value.formData || {}) }
  if (ticket.value.processInstId) {
    try {
      const tl: any = await http.get(`/runtime/timeline/${ticket.value.processInstId}`)
      timeline.value = tl.data || { nodes: [] }
    } catch {
      timeline.value = { nodes: [] }
    }
  } else {
    timeline.value = { nodes: [] }
  }
  await loadMyTask()
}

onMounted(async () => {
  loading.value = true
  try {
    const id = route.params.id as string
    const res: any = await http.get(`/ticket/tickets/${id}`)
    ticket.value = res.data || {}
    formData.value = { ...(ticket.value.formData || {}) }
    const jobs: Promise<any>[] = []
    if (ticket.value.typeId) {
      jobs.push(
        http.get(`/ticket/types/${ticket.value.typeId}/form-ui`, {
          params: { published: true, version: ticket.value.schemaVersion },
        }).then((ui: any) => {
          formSchema.value = ui.data?.schema || { fields: [], raw: [] }
        }),
      )
      jobs.push(
        http.get(`/ticket/types/${ticket.value.typeId}/detail-ui`, {
          params: { published: true, version: ticket.value.schemaVersion },
        }).then((ui: any) => {
          detailSchema.value = ui.data?.schema || detailSchema.value
        }),
      )
    }
    if (ticket.value.processInstId) {
      jobs.push(
        http.get(`/runtime/timeline/${ticket.value.processInstId}`).then((tl: any) => {
          timeline.value = tl.data || { nodes: [] }
        }),
      )
      jobs.push(loadMyTask())
    }
    await Promise.allSettled(jobs)
  } finally {
    loading.value = false
  }
})
</script>
<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.head-actions { display: flex; gap: 8px; align-items: center; }
.task-tip { margin-bottom: 16px; }
.page-title { margin: 0; }
.page-sub { margin: 4px 0 0; color: #7a8a84; }
.link { color: var(--el-color-primary); text-decoration: none; }
.section { margin-bottom: 20px; }
.info {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  overflow: hidden;
}
.info-cell {
  display: flex;
  align-items: center;
  min-height: 40px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.info-cell.wide { grid-column: 1 / -1; }
.info-label {
  flex: 0 0 110px;
  padding: 8px 12px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 13px;
  align-self: stretch;
  display: flex;
  align-items: center;
}
.info-value {
  flex: 1;
  padding: 8px 12px;
  font-size: 13px;
  word-break: break-all;
}
.sec-form { margin-top: 12px; }
h3 { margin: 0 0 12px; font-size: 16px; }
</style>
