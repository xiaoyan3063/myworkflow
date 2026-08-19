<template>
  <div class="page-card">
    <h1 class="page-title">抄送我的</h1>
    <p class="page-sub">知晓类消息，无需审批</p>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="title" label="标题" min-width="200" />
      <el-table-column prop="processInstId" label="实例ID" min-width="180" />
      <el-table-column prop="readFlag" label="已读" width="90">
        <template #default="{ row }">{{ row.readFlag ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="时间" width="180" />
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
    const res: any = await http.get('/runtime/cc', { params: { page: page.value, size: 10 } })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}
onMounted(load)
</script>
<style scoped>.pager{margin-top:16px;display:flex;justify-content:flex-end}</style>
