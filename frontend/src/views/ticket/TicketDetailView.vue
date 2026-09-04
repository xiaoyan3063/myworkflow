<template>
  <div class="page-card" :class="{ embedded }" v-loading="loading">
    <div class="head">
      <div v-if="!embedded">
        <h1 class="page-title">工单详情</h1>
        <p class="page-sub">{{ ticket.ticketNo }} · {{ ticket.title }}</p>
      </div>
      <div v-else></div>
      <div class="head-actions">
        <el-button
          v-if="hasAction('save') && canEdit && hasPerm('ticket:update')"
          type="primary"
          :loading="saving"
          @click="save"
        >保存</el-button>
        <el-button
          v-if="canEditApprovalFields"
          type="primary"
          :loading="saving"
          @click="saveApprovalFields"
        >保存节点数据</el-button>
        <el-button
          v-if="hasAction('submit') && canSubmit && hasPerm('ticket:submit')"
          type="success"
          :loading="submitting"
          @click="submit"
        >提交审批</el-button>
        <el-button
          v-if="canDelete && hasPerm('ticket:delete')"
          type="danger"
          plain
          @click="remove"
        >删除</el-button>
        <ApprovalActions
          v-if="myTask && dataAccess"
          :task="myTask"
          :before-action="saveBeforeAction"
          @done="onActionDone"
        />
        <template v-if="!embedded">
          <el-button v-if="hasAction('cancel')" @click="goBack">返回</el-button>
          <el-button v-else @click="goBack">返回列表</el-button>
        </template>
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
    <el-alert
      v-if="myTask && !dataAccess"
      class="task-tip"
      type="error"
      :closable="false"
      show-icon
      :title="fieldAccess.accessMessage || '当前用户角色没有该工单的数据权限，字段已隐藏且不能审批；授权后请刷新页面'"
    />

    <!-- 抽屉里横向放不下两栏，改成「工单信息 / 审批轨迹」两个页签 -->
    <component :is="embedded ? 'el-tabs' : 'el-row'" v-if="!loading" v-bind="layoutProps">
      <component :is="embedded ? 'el-tab-pane' : 'el-col'" v-bind="mainPaneProps">
        <div
          v-for="(sec, i) in sections"
          v-show="mainOf(sec).length || formOf(sec).length"
          :key="i"
          class="section"
        >
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
            :disabled="!canEdit"
            :editable-fields="canEdit ? undefined : []"
            :hidden-fields="hiddenFields"
            @update:model-value="patchForm"
          />
        </div>

        <div v-if="doneNodeFields.length" class="section">
          <h3>审批节点信息</h3>
          <TicketForm
            class="sec-form"
            :model-value="formData"
            :schema="nodeSchema"
            :only-fields="doneNodeFields"
            :editable-fields="[]"
            disabled
          />
        </div>

        <div v-if="canEditApprovalFields" class="section">
          <h3>本节点填写</h3>
          <TicketForm
            class="sec-form"
            :model-value="formData"
            :schema="nodeSchema"
            :only-fields="nodeFields"
            :editable-fields="nodeFields"
            :required-fields="myTask.requiredFields || []"
            @update:model-value="patchForm"
          />
        </div>
        <TicketChildGroups v-if="ticket.id && dataAccess" ref="childGroupsRef" :parent-id="ticket.id" />
      </component>
      <component :is="embedded ? 'el-tab-pane' : 'el-col'" v-if="showRight" v-bind="timelinePaneProps">
        <h3 v-if="!embedded">审批轨迹</h3>
        <ApprovalTimeline v-if="hasTimeline" :data="timeline" />
        <el-empty v-else description="尚未提交审批" />
      </component>
    </component>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/utils/http'
import TicketForm from '@/components/ticket/TicketForm.vue'
import ApprovalTimeline from '@/components/ApprovalTimeline.vue'
import ApprovalActions from '@/components/ApprovalActions.vue'
import TicketChildGroups from '@/components/ticket/TicketChildGroups.vue'
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

/** 传了 ticketId 就是被主单子表以抽屉方式内嵌，不走路由也不显示返回按钮 */
const props = defineProps<{ ticketId?: string | number; embedded?: boolean }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'changed'): void }>()

const route = useRoute()
const router = useRouter()
const currentId = computed(() => String(props.ticketId ?? route.params.id ?? ''))
const loading = ref(true)
const saving = ref(false)
const submitting = ref(false)
const ticket = ref<any>({})
const formData = ref<Record<string, any>>({})
const formSchema = ref<any>({ fields: [], raw: [] })
/** 节点可填字段可能是工单创建之后才加的，锁定版本里没有，这里单独用最新已发布 schema 渲染 */
const nodeSchema = ref<any>({ fields: [], raw: [] })
const timeline = ref<any>({ nodes: [] })
const myTask = ref<any>(null)
const childGroupsRef = ref<any>(null)
/** 后端按流程走到的节点算出的字段可见性：nodeFields 全部节点字段，hiddenFields 尚未走到的 */
const fieldAccess = ref<any>({})
const detailSchema = ref<any>({
  showTimeline: true,
  sections: [],
  actions: ['save', 'submit'],
})

const isDraftLike = computed(() => ticket.value.status === 'DRAFT')
// 被退回到发起人节点时工单仍是审批中，但持有重提待办的人要能改表单
const canEdit = computed(() => isDraftLike.value || !!myTask.value?.resubmitTask)
const canEditApprovalFields = computed(
  () => dataAccess.value && !canEdit.value
    && Array.isArray(myTask.value?.writableFields) && myTask.value.writableFields.length > 0,
)
const dataAccess = computed(() => fieldAccess.value.dataAccess !== false)
const canSubmit = computed(() => isDraftLike.value)
const canDelete = computed(
  () => ticket.value.status === 'DRAFT' || !!myTask.value?.resubmitTask,
)
const nodeFields = computed<string[]>(() => myTask.value?.writableFields || [])
const hiddenFields = computed<string[]>(() => fieldAccess.value.hiddenFields || [])

/**
 * 归属节点已经走过、但详情配置里没有勾选的字段。
 * 详情配置通常只列发起时的字段，审批中录入的内容不补一块出来就看不到。
 */
const doneNodeFields = computed<string[]>(() => {
  const covered = new Set<string>([...nodeFields.value])
  for (const sec of sections.value) {
    for (const f of sec.fields || []) covered.add(f)
  }
  return (fieldAccess.value.nodeFields || []).filter(
    (f: string) => !covered.has(f) && !hiddenFields.value.includes(f),
  )
})
const activeTab = ref('form')
const layoutProps = computed(() => (props.embedded
  ? { modelValue: activeTab.value, 'onUpdate:modelValue': (v: any) => (activeTab.value = String(v)) }
  : { gutter: 20 }))
const mainPaneProps = computed(() => (props.embedded
  ? { label: '工单信息', name: 'form' }
  : { span: showRight.value ? 14 : 24 }))
const timelinePaneProps = computed(() => (props.embedded
  ? { label: '审批轨迹', name: 'timeline' }
  : { span: 10 }))
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
  const fields = (sec.fields || []).filter(
    (f: string) => !MAIN_TITLE[f] && !hiddenFields.value.includes(f),
  )
  // 本节点可填字段挪到「本节点填写」里，避免同一个字段渲染两遍
  return canEditApprovalFields.value ? fields.filter((f: string) => !nodeFields.value.includes(f)) : fields
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
  if (props.embedded) {
    emit('close')
    return
  }
  const from = route.query.from as string
  if (from) {
    router.push(from)
    return
  }
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
    emit('changed')
    ElMessage.success('已保存')
  } finally {
    saving.value = false
  }
}

function approvalFieldData() {
  const result: Record<string, any> = {}
  for (const field of myTask.value?.writableFields || []) {
    result[field] = formData.value[field]
  }
  return result
}

async function saveApprovalFields(showMessage = true) {
  if (!canEditApprovalFields.value) return
  saving.value = true
  try {
    const res: any = await http.patch(
      `/ticket/tickets/${ticket.value.id}/approval-fields`,
      approvalFieldData(),
    )
    ticket.value = res.data || ticket.value
    formData.value = { ...(ticket.value.formData || formData.value) }
    emit('changed')
    if (showMessage) ElMessage.success('节点数据已保存')
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
    emit('changed')
  } finally {
    submitting.value = false
  }
}

async function remove() {
  const resubmit = !!myTask.value?.resubmitTask
  await ElMessageBox.confirm(
    `删除工单「${ticket.value.ticketNo}」？${resubmit ? '关联的审批流程将同时终止。' : ''}`,
    '确认删除',
    { type: 'warning' },
  )
  await http.delete(`/ticket/tickets/${ticket.value.id}`)
  ElMessage.success('已删除')
  emit('changed')
  goBack()
}

async function loadMyTask() {
  myTask.value = null
  fieldAccess.value = {}
  nodeSchema.value = { fields: [], raw: [] }
  if (!ticket.value.id) return
  try {
    const res: any = await http.get(`/ticket/tickets/${ticket.value.id}/field-access`)
    fieldAccess.value = res.data || {}
    myTask.value = fieldAccess.value.taskId ? fieldAccess.value : null
  } catch {
    fieldAccess.value = {}
  }
  if ((nodeFields.value.length || doneNodeFields.value.length) && ticket.value.typeId) {
    try {
      const ui: any = await http.get(`/ticket/types/${ticket.value.typeId}/form-ui`, {
        params: { published: true },
      })
      nodeSchema.value = ui.data?.schema || formSchema.value
    } catch {
      nodeSchema.value = formSchema.value
    }
  }
}

function missingRequired(): string[] {
  const titles = nodeSchema.value?.fields || []
  return (myTask.value?.requiredFields || []).filter((field: string) => {
    const v = formData.value[field]
    return v === undefined || v === null || v === '' || (Array.isArray(v) && !v.length)
  }).map((field: string) => titles.find((f: any) => f.field === field)?.title || field)
}

/** 办理前先保存当前节点允许填写的数据，后端还会再次校验必填和字段白名单。 */
async function saveBeforeAction() {
  if (myTask.value?.resubmitTask && hasPerm('ticket:update')) {
    await http.put(`/ticket/tickets/${ticket.value.id}`, {
      title: ticket.value.title,
      formData: { ...formData.value },
    })
  } else if (canEditApprovalFields.value) {
    const missing = missingRequired()
    if (missing.length) {
      ElMessage.warning(`请填写本节点必填字段：${missing.join('、')}`)
      throw new Error('required fields missing')
    }
    await saveApprovalFields(false)
  }
}

async function onActionDone() {
  await reload()
  await childGroupsRef.value?.load?.()
  emit('changed')
}

async function reload() {
  const res: any = await http.get(`/ticket/tickets/${currentId.value}`)
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

async function init() {
  loading.value = true
  try {
    const res: any = await http.get(`/ticket/tickets/${currentId.value}`)
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
    }
    await Promise.allSettled(jobs)
    // 草稿也要拿字段可见性，创建阶段同样需要藏掉审批节点字段
    await loadMyTask()
  } finally {
    loading.value = false
  }
}

onMounted(init)
// 主单和明细单共用同一条路由，只换参数时组件会被复用；抽屉里换单据同理
watch(currentId, (id, previous) => {
  if (id && id !== previous) init()
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
/* 抽屉里已经有自己的留白和标题栏，去掉页面级卡片样式 */
.page-card.embedded { padding: 0; background: transparent; box-shadow: none; border: none; }
.page-card.embedded .head { margin-bottom: 12px; }
h3 { margin: 0 0 12px; font-size: 16px; }
</style>
