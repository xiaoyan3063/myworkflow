<template>
  <div class="page-card">
    <h1 class="page-title">我的待办</h1>
    <p class="page-sub">等待你处理的审批任务</p>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column prop="taskName" label="当前节点" width="140" />
      <el-table-column prop="starterName" label="发起人" width="150" />
      <el-table-column prop="createTime" label="到达时间" width="180" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="$router.push(`/task/${row.taskId}`)">办理</el-button>
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
import http from '@/utils/http'

const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res: any = await http.get('/runtime/todo', { params: { page: page.value, size: 10 } })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
