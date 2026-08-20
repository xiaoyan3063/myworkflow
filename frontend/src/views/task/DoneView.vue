<template>
  <div class="page-card">
    <h1 class="page-title">我的已办</h1>
    <p class="page-sub">你已处理过的单据，一张单据只显示一条，展示你最近一次的处理节点</p>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column label="最近处理节点" width="180">
        <template #default="{ row }">
          {{ row.taskName }}
          <el-tag v-if="row.handledCount > 1" size="small" type="info" effect="plain">
            共处理 {{ row.handledCount }} 次
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="starterName" label="发起人" width="120" />
      <el-table-column prop="endTime" label="最近处理时间" width="180" />
      <el-table-column label="当前审批人" width="150">
        <template #default="{ row }">{{ row.currentApprover || '—' }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTone(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="view(row.processInstanceId)">查看轨迹</el-button>
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
import { useRouter } from 'vue-router'
import http from '@/utils/http'
import { statusText, statusTone } from '@/utils/status'
const router = useRouter()
const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
async function load() {
  loading.value = true
  try {
    const res: any = await http.get('/runtime/done', { params: { page: page.value, size: 10 } })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}
function view(processInstanceId: string) {
  router.push(`/instance/${processInstanceId}`)
}
onMounted(load)
</script>
<style scoped>.pager{margin-top:16px;display:flex;justify-content:flex-end}</style>
