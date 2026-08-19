<template>
  <div class="page-card">
    <div class="head">
      <div>
        <h1 class="page-title">表单管理</h1>
        <p class="page-sub">为流程绑定动态业务表单</p>
      </div>
      <el-button type="primary" @click="$router.push('/forms/design')">新建表单</el-button>
    </div>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="formName" label="名称" min-width="160" />
      <el-table-column prop="formKey" label="标识" width="160" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">{{ row.status === 1 ? '启用' : '停用' }}</template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="180" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/forms/design/${row.id}`)">设计</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '@/utils/http'
const list = ref<any[]>([])
const loading = ref(false)
onMounted(async () => {
  loading.value = true
  try {
    const res: any = await http.get('/process/forms', { params: { page: 1, size: 50 } })
    list.value = res.data?.records || []
  } finally { loading.value = false }
})
</script>
<style scoped>.head{display:flex;justify-content:space-between;align-items:flex-start}</style>
