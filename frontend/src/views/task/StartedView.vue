<template>
  <div class="page-card">
    <h1 class="page-title">我发起的</h1>
    <p class="page-sub">由你发起的流程实例</p>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column prop="processKey" label="流程标识" width="140" />
      <el-table-column prop="businessKey" label="业务单号" width="140" />
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row.status === 'COMPLETED' ? 'success' : row.status === 'REJECTED' ? 'danger' : 'warning'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startTime" label="发起时间" width="180" />
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
    const res: any = await http.get('/runtime/started', { params: { page: page.value, size: 10 } })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}
onMounted(load)
</script>
<style scoped>.pager{margin-top:16px;display:flex;justify-content:flex-end}</style>
