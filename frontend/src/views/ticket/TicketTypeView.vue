<template>
  <div class="page-card">
    <div class="head">
      <div>
        <h1 class="page-title">工单类型</h1>
        <p class="page-sub">维护工单分类；表单用 FcDesigner，列表用字段勾选</p>
      </div>
      <el-button type="primary" @click="openType()">新建类型</el-button>
    </div>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="typeName" label="名称" min-width="140" />
      <el-table-column prop="typeCode" label="编码" width="140" />
      <el-table-column prop="processKey" label="绑定流程" width="160">
        <template #default="{ row }">{{ row.processKey || '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">{{ row.status === 1 ? '启用' : '停用' }}</template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="180" />
      <el-table-column label="操作" width="420" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openType(row)">编辑</el-button>
          <el-button link type="primary" @click="$router.push(`/ticket-types/${row.id}/form`)">设计表单</el-button>
          <el-button link type="primary" @click="$router.push(`/ticket-types/${row.id}/list`)">配置列表</el-button>
          <el-button link type="primary" @click="$router.push(`/tickets/${row.typeCode}`)">打开列表</el-button>
          <el-button link type="danger" @click="removeType(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination background layout="total, prev, pager, next" :total="total" v-model:current-page="page" @current-change="load" />
    </div>

    <el-dialog v-model="typeVisible" :title="typeForm.id ? '编辑类型' : '新建类型'" width="480px">
      <el-form label-width="100px">
        <el-form-item label="名称"><el-input v-model="typeForm.typeName" /></el-form-item>
        <el-form-item label="编码">
          <el-input v-model="typeForm.typeCode" :disabled="!!typeForm.id" placeholder="如 LEAVE，用于工单号前缀" />
        </el-form-item>
        <el-form-item label="绑定流程">
          <el-input v-model="typeForm.processKey" placeholder="已发布流程 processKey，本步不发起" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="typeForm.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeVisible = false">取消</el-button>
        <el-button type="primary" @click="saveType">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/utils/http'

const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const typeVisible = ref(false)
const typeForm = reactive<any>({})

async function load() {
  loading.value = true
  try {
    const res: any = await http.get('/ticket/types', { params: { page: page.value, size: 10 } })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

function openType(row?: any) {
  Object.keys(typeForm).forEach(k => delete typeForm[k])
  Object.assign(typeForm, row ? { ...row } : { status: 1 })
  typeVisible.value = true
}

async function saveType() {
  if (!typeForm.typeName || !typeForm.typeCode) return ElMessage.warning('请填写名称和编码')
  await http.post('/ticket/types', typeForm)
  ElMessage.success('已保存')
  typeVisible.value = false
  load()
}

async function removeType(row: any) {
  await ElMessageBox.confirm(`删除类型「${row.typeName}」？`, '确认')
  await http.delete(`/ticket/types/${row.id}`)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
