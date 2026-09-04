<template>
  <div v-for="group in groups" :key="group.relation.id" class="child-group">
    <div class="child-head">
      <h3>
        {{ group.relation.relationName }}
        <span class="rows-tip">{{ rowsTip(group) }}</span>
      </h3>
      <el-button
        v-if="group.access.allowAppend"
        type="primary"
        size="small"
        :disabled="reachedMax(group)"
        @click="openEdit(group)"
      >
        新增明细
      </el-button>
    </div>
    <el-table :data="group.children" border empty-text="暂无明细">
      <el-table-column prop="ticketNo" label="明细单号" min-width="170" show-overflow-tooltip />
      <el-table-column
        v-for="field in group.fields"
        :key="field.fieldKey"
        :label="field.title"
        min-width="140"
        show-overflow-tooltip
      >
        <template #default="{ row }">{{ displayValue(row.formData?.[field.fieldKey], field) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110" fixed="right">
        <template #default="{ row }">
          <el-tag size="small" :type="ticketStatusTone(row.status)">{{ ticketStatusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button v-if="group.access.allowEdit && row.status === 'DRAFT'" link type="primary" @click="openEdit(group, row)">编辑</el-button>
          <el-button v-if="row.status === 'DRAFT'" link type="success" @click="submitChild(group, row)">提交</el-button>
          <el-button link type="primary" @click="openChild(group, row)">详情</el-button>
          <el-button v-if="group.access.allowDelete && row.status === 'DRAFT'" link type="danger" @click="removeChild(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <el-drawer v-model="childVisible" :size="drawerSize" :title="childTitle" destroy-on-close>
    <TicketDetailPanel
      v-if="childVisible"
      :ticket-id="childId"
      embedded
      @close="childVisible = false"
      @changed="load"
    />
  </el-drawer>

  <el-dialog v-model="visible" :title="editing.id ? '编辑明细' : '新增明细'" width="720px">
    <TicketForm
      v-if="visible"
      :model-value="formData"
      :schema="activeGroup?.formSchema || { fields: [], raw: [] }"
      :editable-fields="activeGroup?.access?.writableFields || []"
      :required-fields="activeGroup?.access?.requiredFields || []"
      :hidden-fields="activeGroup?.hiddenFields || []"
      @update:model-value="patchForm"
    />
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveChild">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/utils/http'
import TicketForm from './TicketForm.vue'
import { ticketStatusText, ticketStatusTone } from '@/utils/status'
import { loadUserNames, userNamesOf } from '@/utils/userNames'

// 详情页里也会渲染子表，异步引入打断循环依赖
const TicketDetailPanel = defineAsyncComponent(() => import('@/views/ticket/TicketDetailView.vue'))

const props = defineProps<{ parentId: string | number }>()
const groups = ref<any[]>([])
const activeGroup = ref<any>(null)
const editing = ref<any>({})
const formData = ref<Record<string, any>>({})
const visible = ref(false)
const saving = ref(false)
const childVisible = ref(false)
const childId = ref('')
const childTitle = ref('')
// 窄屏放不下一半，退回整屏
const drawerSize = computed(() => (window.innerWidth < 1200 ? '90%' : '50%'))

function displayValue(value: any, field?: any) {
  if (field?.fieldType === 'user' || field?.fieldType === 'users') return userNamesOf(value) || '-'
  let labels: Record<string, string> = {}
  try {
    labels = Object.fromEntries(
      JSON.parse(field?.optionsJson || '[]').map((item: any) => [String(item.value), item.label]),
    )
  } catch {
    labels = {}
  }
  if (Array.isArray(value)) return value.map(v => labels[String(v)] || v).join('、')
  if (labels[String(value)] !== undefined) return labels[String(value)]
  if (value && typeof value === 'object') return JSON.stringify(value)
  return value ?? '-'
}

/** 作废的明细不计入条数限制，与后端保持一致 */
function rowCount(group: any) {
  return (group.children || []).filter((row: any) => row.status !== 'CANCELLED').length
}

function reachedMax(group: any) {
  const max = Number(group.access?.maxRows) || 0
  return max > 0 && rowCount(group) >= max
}

function rowsTip(group: any) {
  const min = Number(group.access?.minRows) || 0
  const max = Number(group.access?.maxRows) || 0
  const count = rowCount(group)
  if (!min && !max) return `共 ${count} 条`
  if (min && !max) return `共 ${count} 条，至少 ${min} 条`
  if (!min && max) return `共 ${count} 条，最多 ${max} 条`
  return `共 ${count} 条，需 ${min} ~ ${max} 条`
}

function patchForm(value: Record<string, any>) {
  formData.value = { ...formData.value, ...value }
}

async function load() {
  const res: any = await http.get(`/ticket/tickets/${props.parentId}/children`)
  groups.value = res.data || []
  const ids: any[] = []
  for (const group of groups.value) {
    const userFields = (group.fields || [])
      .filter((field: any) => field.fieldType === 'user' || field.fieldType === 'users')
      .map((field: any) => field.fieldKey)
    for (const row of group.children || []) {
      userFields.forEach((field: string) => ids.push(row.formData?.[field]))
    }
  }
  await loadUserNames(ids)
}

function openEdit(group: any, row?: any) {
  activeGroup.value = group
  editing.value = row || {}
  formData.value = { ...(row?.formData || {}) }
  visible.value = true
}

function missingRequired() {
  const required = activeGroup.value?.access?.requiredFields || []
  const fields = activeGroup.value?.fields || []
  return required.filter((field: string) => {
    const value = formData.value[field]
    return value === undefined || value === null || value === ''
      || (Array.isArray(value) && !value.length)
  }).map((field: string) => fields.find((f: any) => f.fieldKey === field)?.title || field)
}

async function saveChild() {
  const missing = missingRequired()
  if (missing.length) return ElMessage.warning(`请填写必填字段：${missing.join('、')}`)
  saving.value = true
  try {
    if (editing.value.id) {
      await http.put(`/ticket/tickets/${props.parentId}/children/${editing.value.id}`, formData.value)
    } else {
      await http.post(
        `/ticket/tickets/${props.parentId}/children/${activeGroup.value.relation.id}`,
        formData.value,
      )
    }
    ElMessage.success('明细已保存')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function submitChild(group: any, row: any) {
  await ElMessageBox.confirm(`提交明细「${row.ticketNo}」并启动子流程？`, '确认')
  await http.post(`/ticket/tickets/${props.parentId}/children/${row.id}/submit`)
  ElMessage.success('明细已提交')
  await load()
}

async function removeChild(row: any) {
  await ElMessageBox.confirm(`删除明细「${row.ticketNo}」？`, '确认')
  await http.delete(`/ticket/tickets/${props.parentId}/children/${row.id}`)
  ElMessage.success('明细已删除')
  await load()
}

/** 明细详情走侧边抽屉，主单信息留在下面不被顶掉 */
function openChild(group: any, row: any) {
  childId.value = String(row.id)
  childTitle.value = `${group.relation?.relationName || '明细'} · ${row.ticketNo || ''}`
  childVisible.value = true
}

onMounted(load)
// 从主单跳到明细单时路由复用同一个详情页，组件不会重新挂载
watch(() => props.parentId, () => { load() })
defineExpose({ load })
</script>

<style scoped>
.child-group { margin-top: 20px; }
.child-head { display: flex; align-items: center; justify-content: space-between; }
.child-head h3 { margin: 0 0 10px; }
.rows-tip { margin-left: 8px; font-size: 12px; font-weight: normal; color: #7a8a84; }
</style>
