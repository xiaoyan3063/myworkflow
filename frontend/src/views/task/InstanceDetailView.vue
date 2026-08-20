<template>
  <div class="page-card" v-loading="loading">
    <h1 class="page-title">审批详情</h1>
    <p class="page-sub">{{ detail.title }}</p>
    <el-row :gutter="20">
      <el-col :span="14">
        <el-descriptions title="基本信息" :column="2" border>
          <el-descriptions-item label="发起人">{{ detail.starterName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTone(detail.status)">{{ statusText(detail.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="业务单号">{{ detail.businessKey || '-' }}</el-descriptions-item>
          <el-descriptions-item label="业务类型">{{ detail.businessType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发起时间">{{ detail.startTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ detail.endTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前审批人" :span="2">
            {{ detail.currentApprover || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-divider>表单数据</el-divider>
        <el-descriptions v-if="fields.length" :column="1" border>
          <el-descriptions-item v-for="f in fields" :key="f.field" :label="f.title || f.field">
            {{ display(f) }}
          </el-descriptions-item>
        </el-descriptions>
        <pre v-else class="form-box">{{ prettyForm }}</pre>
      </el-col>

      <el-col :span="10">
        <h3>审批轨迹</h3>
        <ApprovalTimeline :data="timeline" />
      </el-col>
    </el-row>

    <div class="footer">
      <el-button @click="router.back()">返回</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import http from '@/utils/http'
import { statusText, statusTone } from '@/utils/status'
import ApprovalTimeline from '@/components/ApprovalTimeline.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref<any>({})
const timeline = ref<any>({ nodes: [] })
const formData = ref<Record<string, any>>({})

const fields = computed<any[]>(() => detail.value.formSchema || [])

const prettyForm = computed(() => {
  try {
    return JSON.stringify(JSON.parse(detail.value.formData || '{}'), null, 2)
  } catch {
    return detail.value.formData || '{}'
  }
})

/** 下拉和人员字段存的是值，展示要翻回标签 */
function display(field: any) {
  const raw = formData.value[field.field]
  if (raw === undefined || raw === null || raw === '') return '-'
  if (field.type === 'select') {
    const hit = (field.options || []).find((o: any) => String(o.value) === String(raw))
    return hit?.label ?? raw
  }
  return Array.isArray(raw) ? raw.join('、') : raw
}

async function load() {
  loading.value = true
  try {
    const instanceId = route.params.processInstanceId as string
    const res: any = await http.get(`/runtime/instances/${instanceId}`)
    detail.value = res.data || {}
    try {
      formData.value = JSON.parse(detail.value.formData || '{}')
    } catch {
      formData.value = {}
    }
    const tl: any = await http.get(`/runtime/timeline/${instanceId}`)
    timeline.value = tl.data || { nodes: [] }
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.form-box {
  background: #e8f2ec;
  border-radius: 12px;
  padding: 14px;
  overflow: auto;
  max-height: 240px;
}
h3 { color: #0b3d2e; }
.footer {
  margin-top: 20px;
}
</style>
