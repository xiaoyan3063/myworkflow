<template>
  <div class="page-card">
    <div class="head">
      <div>
        <h1 class="page-title">工单列表</h1>
        <p class="page-sub">草稿可提交审批；审批中不可改字段</p>
      </div>
      <el-button v-if="hasPerm('ticket:create')" type="primary" :disabled="!typeId" @click="openEdit()">新建草稿</el-button>
    </div>
    <el-form inline>
      <el-form-item label="工单类型">
        <el-select v-model="typeId" placeholder="请选择" clearable filterable style="width: 220px" @change="onTypeChange">
          <el-option v-for="t in types" :key="t.id" :label="t.typeName" :value="t.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-input v-model="keyword" placeholder="工单号 / 标题" clearable @keyup.enter="load" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load">查询</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="ticketNo" label="工单号" width="180" />
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column prop="typeName" label="类型" width="120" />
      <el-table-column prop="starterName" label="发起人" width="100" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="ticketStatusTone(row.status)">{{ ticketStatusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当前审批人" min-width="140">
        <template #default="{ row }">{{ row.currentApprover || '—' }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/tickets/${row.typeCode}/${row.id}`)">查看</el-button>
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/utils/http'
import TicketForm from '@/components/ticket/TicketForm.vue'
import { ticketStatusText, ticketStatusTone } from '@/utils/status'
import { hasPerm } from '@/utils/permission'

const types = ref<any[]>([])
const list = ref<any[]>([])
const typeId = ref<string>()
const keyword = ref('')
const total = ref(0)
const page = ref(1)
const loading = ref(false)
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

async function loadSchema(id: string) {
  const res: any = await http.get(`/ticket/types/${id}/form-ui`)
  formSchema.value = res.data?.schema || { fields: [], raw: [] }
}

async function onTypeChange() {
  page.value = 1
  if (typeId.value) await loadSchema(typeId.value)
  else formSchema.value = { fields: [], raw: [] }
  load()
}

async function load() {
  loading.value = true
  try {
    const res: any = await http.get('/ticket/tickets', {
      params: { page: page.value, size: 10, typeId: typeId.value, keyword: keyword.value || undefined },
    })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

async function openEdit(row?: any) {
  const tid = row?.typeId || typeId.value
  if (!tid) return ElMessage.warning('请先选择工单类型')
  await loadSchema(tid)
  Object.keys(form).forEach(k => delete form[k])
  if (row) {
    Object.assign(form, { id: row.id, typeId: row.typeId, title: row.title })
    formData.value = { ...(row.formData || {}) }
  } else {
    form.typeId = tid
    form.title = ''
    formData.value = {}
  }
  visible.value = true
}

async function save() {
  if (!form.title) return ElMessage.warning('请填写标题')
  const payload = { typeId: form.typeId || typeId.value, title: form.title, formData: { ...formData.value } }
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

onMounted(async () => {
  const t: any = await http.get('/ticket/types/enabled')
  types.value = t.data || []
  if (types.value.length) {
    typeId.value = types.value[0].id
    await loadSchema(typeId.value!)
  }
  load()
})
</script>
<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
