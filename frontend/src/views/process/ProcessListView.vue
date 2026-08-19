<template>
  <div class="page-card">
    <div class="head">
      <div>
        <h1 class="page-title">流程管理</h1>
        <p class="page-sub">设计、发布与维护审批流程</p>
      </div>
      <el-button type="primary" @click="$router.push('/process/design')">新建流程</el-button>
    </div>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="processName" label="名称" min-width="160" />
      <el-table-column prop="processKey" label="标识" width="160" />
      <el-table-column prop="version" label="版本" width="80" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'info' : 'warning'">
            {{ row.status === 1 ? '已发布' : row.status === 2 ? '停用' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="180" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/process/design/${row.id}`)">设计</el-button>
          <el-button link type="success" @click="deploy(row.id)" :disabled="row.status === 1 && !row.bpmnXml">发布</el-button>
          <el-button link @click="disable(row.id)" :disabled="row.status !== 1">停用</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination background layout="total, prev, pager, next" :total="total" v-model:current-page="page" @current-change="load" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/utils/http'

const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res: any = await http.get('/process/defs', { params: { page: page.value, size: 10 } })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

async function deploy(id: number) {
  await http.post(`/process/defs/${id}/deploy`)
  ElMessage.success('发布成功')
  load()
}

async function disable(id: number) {
  await http.post(`/process/defs/${id}/disable`)
  ElMessage.success('已停用')
  load()
}

async function remove(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认删除流程「${row.processName}」？已发布的流程定义与其历史数据将一并移除，操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await http.delete(`/process/defs/${row.id}`)
  ElMessage.success('已删除')
  if (list.value.length === 1 && page.value > 1) page.value -= 1
  load()
}

onMounted(load)
</script>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
