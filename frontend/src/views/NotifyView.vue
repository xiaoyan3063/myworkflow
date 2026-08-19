<template>
  <div class="page-card">
    <div class="head">
      <div>
        <h1 class="page-title">消息中心</h1>
        <p class="page-sub">待办、抄送、超时催办等站内通知</p>
      </div>
      <el-button type="primary" plain :disabled="!unreadCount" @click="readAll">
        全部标为已读{{ unreadCount ? `（${unreadCount}）` : '' }}
      </el-button>
    </div>
    <el-table :data="list" v-loading="loading" :row-class-name="rowClass">
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column prop="content" label="内容" min-width="240" />
      <el-table-column prop="msgType" label="类型" width="100" />
      <el-table-column label="已读" width="90">
        <template #default="{ row }">
          <el-tag :type="row.readFlag === 1 ? 'info' : 'danger'" effect="light">
            {{ row.readFlag === 1 ? '已读' : '未读' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="时间" width="180" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="row.readFlag === 1" @click="read(row)">标为已读</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/utils/http'

const list = ref<any[]>([])
const loading = ref(false)
const unreadCount = computed(() => list.value.filter((m) => m.readFlag !== 1).length)

function rowClass({ row }: any) {
  return row.readFlag === 1 ? '' : 'unread-row'
}

async function load() {
  loading.value = true
  try {
    const res: any = await http.get('/notify/messages', { params: { page: 1, size: 50 } })
    list.value = res.data?.records || []
  } finally {
    loading.value = false
  }
}

async function read(row: any) {
  await http.post(`/notify/messages/${row.id}/read`)
  row.readFlag = 1
}

async function readAll() {
  const unread = list.value.filter((m) => m.readFlag !== 1)
  await Promise.all(unread.map((m) => http.post(`/notify/messages/${m.id}/read`)))
  unread.forEach((m) => (m.readFlag = 1))
  ElMessage.success('已全部标为已读')
}

onMounted(load)
</script>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; }
:deep(.unread-row) { font-weight: 600; }
</style>
