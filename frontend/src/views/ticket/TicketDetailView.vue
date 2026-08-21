<template>
  <div class="page-card" v-loading="loading">
    <div class="head">
      <div>
        <h1 class="page-title">工单详情</h1>
        <p class="page-sub">{{ ticket.ticketNo }} · {{ ticket.title }}</p>
      </div>
      <el-button @click="router.push('/tickets')">返回列表</el-button>
    </div>

    <!-- 数据到齐后再挂载，避免子组件停留在首次渲染的空数据上 -->
    <el-row v-if="!loading" :gutter="20">
      <el-col :span="14">
        <div class="info">
          <div class="info-cell">
            <span class="info-label">工单号</span>
            <span class="info-value">{{ ticket.ticketNo || '-' }}</span>
          </div>
          <div class="info-cell">
            <span class="info-label">状态</span>
            <span class="info-value">
              <el-tag size="small" :type="ticketStatusTone(ticket.status)">{{ ticketStatusText(ticket.status) }}</el-tag>
            </span>
          </div>
          <div class="info-cell">
            <span class="info-label">类型</span>
            <span class="info-value">{{ ticket.typeName || '-' }}</span>
          </div>
          <div class="info-cell">
            <span class="info-label">发起人</span>
            <span class="info-value">{{ ticket.starterName || '-' }}</span>
          </div>
          <div class="info-cell">
            <span class="info-label">当前审批人</span>
            <span class="info-value">{{ ticket.currentApprover || '-' }}</span>
          </div>
          <div class="info-cell">
            <span class="info-label">绑定流程</span>
            <span class="info-value">{{ ticket.processKey || '-' }}</span>
          </div>
          <div class="info-cell">
            <span class="info-label">提交时间</span>
            <span class="info-value">{{ timeline.startTime || '-' }}</span>
          </div>
          <div class="info-cell">
            <span class="info-label">结束时间</span>
            <span class="info-value">{{ timeline.endTime || '-' }}</span>
          </div>
          <div class="info-cell wide">
            <span class="info-label">流程实例</span>
            <span class="info-value">
              <router-link v-if="ticket.processInstId" class="link" :to="`/instance/${ticket.processInstId}`">
                {{ ticket.processInstId }}
              </router-link>
              <template v-else>-</template>
            </span>
          </div>
        </div>
        <el-divider>表单</el-divider>
        <TicketForm v-model="formData" :schema="formSchema" disabled />
      </el-col>
      <el-col :span="10">
        <h3>审批轨迹</h3>
        <ApprovalTimeline v-if="timeline.startTime || (timeline.nodes || []).length" :data="timeline" />
        <el-empty v-else description="尚未提交审批" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import http from '@/utils/http'
import TicketForm from '@/components/ticket/TicketForm.vue'
import ApprovalTimeline from '@/components/ApprovalTimeline.vue'
import { ticketStatusText, ticketStatusTone } from '@/utils/status'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const ticket = ref<any>({})
const formData = ref<Record<string, any>>({})
const formSchema = ref<any>({ fields: [], raw: [] })
const timeline = ref<any>({ nodes: [] })
onMounted(async () => {
  loading.value = true
  try {
    const id = route.params.id as string
    const res: any = await http.get(`/ticket/tickets/${id}`)
    ticket.value = res.data || {}
    formData.value = { ...(ticket.value.formData || {}) }

    // 表单和轨迹各自独立，取表单失败不能把轨迹一起吞掉
    const jobs: Promise<any>[] = []
    if (ticket.value.typeId) {
      jobs.push(
        http.get(`/ticket/types/${ticket.value.typeId}/form-ui`).then((ui: any) => {
          formSchema.value = ui.data?.schema || { fields: [], raw: [] }
        }),
      )
    }
    if (ticket.value.processInstId) {
      jobs.push(
        http.get(`/runtime/timeline/${ticket.value.processInstId}`).then((tl: any) => {
          timeline.value = tl.data || { nodes: [] }
        }),
      )
    }
    await Promise.allSettled(jobs)
  } finally {
    loading.value = false
  }
})
</script>
<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.page-title { margin: 0; }
.page-sub { margin: 4px 0 0; color: #7a8a84; }
.link { color: var(--el-color-primary); text-decoration: none; }
.info {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  overflow: hidden;
}
.info-cell {
  display: flex;
  align-items: center;
  min-height: 40px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.info-cell.wide { grid-column: 1 / -1; }
.info-label {
  flex: 0 0 110px;
  padding: 8px 12px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 13px;
  align-self: stretch;
  display: flex;
  align-items: center;
}
.info-value {
  flex: 1;
  padding: 8px 12px;
  font-size: 13px;
  word-break: break-all;
}
h3 { margin: 0 0 12px; font-size: 16px; }
</style>
