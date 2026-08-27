<template>
  <div class="page-card">
    <div class="head">
      <div>
        <h1 class="page-title">{{ type.typeName || '工单列表' }}</h1>
        <p class="page-sub">{{ type.typeCode }} · 列和筛选来自已发布的列表配置</p>
      </div>
      <el-button v-if="hasPerm('ticket:create')" type="primary" @click="openEdit()">新建草稿</el-button>
    </div>

    <el-form inline @submit.prevent="search">
      <el-form-item v-for="f in filters" :key="f.field" :label="filterLabel(f)">
        <el-select v-if="f.field === 'status'" v-model="query[f.field]" clearable placeholder="全部" style="width: 140px">
          <el-option v-for="(meta, key) in TICKET_STATUS" :key="key" :label="meta.text" :value="key" />
        </el-select>
        <el-date-picker
          v-else-if="f.field === 'createTime'"
          v-model="query[f.field]"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="创建时间"
          clearable
          style="width: 190px"
        />
        <el-input v-else v-model="query[f.field]" clearable :placeholder="f.op === 'like' ? '模糊匹配' : '精确匹配'" @keyup.enter="search" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading">
      <el-table-column
        v-for="col in columns"
        :key="col.field"
        :prop="col.field"
        :label="col.title"
        :width="col.width || undefined"
        :min-width="col.width ? undefined : 120"
      >
        <template #default="{ row }">
          <el-tag v-if="col.field === 'status'" size="small" :type="ticketStatusTone(row.status)">
            {{ ticketStatusText(row.status) }}
          </el-tag>
          <TicketFileLinks v-else-if="isFileCell(row, col)" :value="row.formData?.[col.field]" />
          <span v-else>{{ cellValue(row, col) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/tickets/${typeCode}/${row.id}`)">查看</el-button>
          <el-button v-if="canEdit(row) && hasPerm('ticket:update')" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="canSubmit(row) && hasPerm('ticket:submit')" link type="success" @click="submit(row)">提交</el-button>
          <el-button v-if="row.status === 'DRAFT' && hasPerm('ticket:delete')" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination background layout="total, prev, pager, next" :total="total" v-model:current-page="page" @current-change="load" />
    </div>

    <el-dialog v-model="visible" :title="form.id ? '编辑工单' : '新建草稿'" width="640px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" />
        </el-form-item>
      </el-form>
      <TicketForm v-model="formData" :schema="formSchema" />
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/utils/http'
import TicketForm from '@/components/ticket/TicketForm.vue'
import TicketFileLinks from '@/components/ticket/TicketFileLinks.vue'
import { looksLikeFileIds, normalizeFileIds } from '@/components/ticket/ticketFiles'
import { loadUserNames, toIds, userNamesOf } from '@/utils/userNames'
import { TICKET_STATUS, ticketStatusText, ticketStatusTone } from '@/utils/status'
import { hasPerm } from '@/utils/permission'

const route = useRoute()
const router = useRouter()
const typeCode = computed(() => String(route.params.typeCode || ''))
const type = ref<any>({})
const columns = ref<any[]>([])
const filters = ref<any[]>([])
const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const query = reactive<Record<string, any>>({})
const visible = ref(false)
const form = reactive<any>({})
const formData = ref<Record<string, any>>({})
const formSchema = ref<any>({ fields: [], raw: [] })

function canEdit(row: any) {
  return row.status === 'DRAFT' || row.status === 'REJECTED'
}
function canSubmit(row: any) {
  return row.status === 'DRAFT' || row.status === 'REJECTED'
}

function mainProp(field: string) {
  if (field === 'ticket_no') return 'ticketNo'
  if (field === 'createTime' || field === 'create_time') return 'createTime'
  return field
}

const fileFields = computed(
  () => new Set((formSchema.value?.fields || []).filter((f: any) => f.type === 'file').map((f: any) => f.field)),
)

const userFields = computed(
  () =>
    new Set(
      (formSchema.value?.fields || [])
        .filter((f: any) => f.type === 'user' || f.type === 'users')
        .map((f: any) => f.field),
    ),
)

/** 字段改过名、旧数据对不上已发布 schema 时，按值的形状兜底认成附件 */
function isFileCell(row: any, col: any) {
  if (col.from !== 'json') return false
  const v = row.formData?.[col.field]
  return fileFields.value.has(col.field) ? normalizeFileIds(v).length > 0 : looksLikeFileIds(v)
}

function cellValue(row: any, col: any) {
  if (col.from === 'json') {
    const v = row.formData?.[col.field]
    if (v === undefined || v === null || v === '') return '-'
    if (userFields.value.has(col.field)) return userNamesOf(v) || '-'
    return Array.isArray(v) ? v.join('、') : String(v)
  }
  const v = row[mainProp(col.field)]
  return v === undefined || v === null || v === '' ? '-' : v
}

function filterLabel(f: any) {
  const hit = columns.value.find((c) => c.field === f.field)
  if (hit?.title) return hit.title
  if (f.field === 'ticket_no') return '工单号'
  if (f.field === 'title') return '标题'
  if (f.field === 'status') return '状态'
  if (f.field === 'createTime') return '创建时间'
  return f.field
}

function search() {
  page.value = 1
  load()
}

function reset() {
  Object.keys(query).forEach((k) => { query[k] = undefined })
  search()
}

async function loadSchema() {
  const t: any = await http.get(`/ticket/types/code/${typeCode.value}`)
  type.value = t.data || {}
  const ui: any = await http.get(`/ticket/types/${type.value.id}/list-ui`, { params: { published: true } })
  const schema = ui.data?.schema || {}
  columns.value = schema.columns || []
  filters.value = schema.filters || []
  const formUi: any = await http.get(`/ticket/types/${type.value.id}/form-ui`, { params: { published: true } })
  formSchema.value = formUi.data?.schema || { fields: [], raw: [] }
}

async function load() {
  loading.value = true
  try {
    const params: Record<string, any> = { page: page.value, size: 10 }
    for (const f of filters.value) {
      const v = query[f.field]
      if (v !== undefined && v !== null && String(v).trim() !== '') {
        params[f.field] = v
      }
    }
    const res: any = await http.get(`/ticket/tickets/by-type/${typeCode.value}`, { params })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
    await loadCellUserNames()
  } finally {
    loading.value = false
  }
}

/** 人员字段单元格拿到的是用户 id，渲染前先把这一页涉及的人名查回来 */
async function loadCellUserNames() {
  if (!userFields.value.size) return
  const ids: string[] = []
  for (const row of list.value) {
    for (const field of userFields.value) {
      ids.push(...toIds(row.formData?.[field as string]))
    }
  }
  await loadUserNames(ids)
}

async function openEdit(row?: any) {
  Object.keys(form).forEach((k) => delete form[k])
  if (row) {
    Object.assign(form, { id: row.id, typeId: row.typeId, title: row.title })
    formData.value = { ...(row.formData || {}) }
  } else {
    form.typeId = type.value.id
    form.title = ''
    formData.value = {}
  }
  visible.value = true
}

async function save() {
  if (!form.title) return ElMessage.warning('请填写标题')
  const payload = { typeId: form.typeId || type.value.id, title: form.title, formData: { ...formData.value } }
  if (form.id) {
    await http.put(`/ticket/tickets/${form.id}`, payload)
  } else {
    await http.post('/ticket/tickets', payload)
  }
  ElMessage.success('已保存')
  visible.value = false
  load()
}

async function submit(row: any) {
  await ElMessageBox.confirm(`提交工单「${row.ticketNo}」进入审批？`, '确认')
  await http.post(`/ticket/tickets/${row.id}/submit`)
  ElMessage.success('已提交审批')
  load()
}

async function remove(row: any) {
  await ElMessageBox.confirm(`删除草稿「${row.ticketNo}」？`, '确认')
  await http.delete(`/ticket/tickets/${row.id}`)
  ElMessage.success('已删除')
  load()
}

async function boot() {
  if (/^\d{15,}$/.test(typeCode.value)) {
    const res: any = await http.get(`/ticket/tickets/${typeCode.value}`)
    const t = res.data
    if (t?.typeCode && t?.id) {
      router.replace(`/tickets/${t.typeCode}/${t.id}`)
      return
    }
  }
  await loadSchema()
  await load()
}

onMounted(boot)
watch(typeCode, () => {
  page.value = 1
  Object.keys(query).forEach((k) => { query[k] = undefined })
  boot()
})
</script>
<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
