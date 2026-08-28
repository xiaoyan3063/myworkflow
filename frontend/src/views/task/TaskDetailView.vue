<template>
  <div class="page-card" v-loading="loading">
    <h1 class="page-title">任务办理</h1>
    <p class="page-sub">{{ detail.taskName }} · {{ detail.title }}</p>
    <el-row :gutter="20">
      <el-col :span="14">
        <el-descriptions title="基本信息" :column="2" border>
          <el-descriptions-item label="发起人">{{ detail.starterName }}</el-descriptions-item>
          <el-descriptions-item label="业务单号">{{ detail.businessKey || '-' }}</el-descriptions-item>
          <el-descriptions-item label="到达时间">{{ detail.createTime }}</el-descriptions-item>
          <el-descriptions-item label="实例ID">{{ detail.processInstanceId }}</el-descriptions-item>
        </el-descriptions>
        <el-divider>表单数据</el-divider>
        <el-alert
          v-if="detail.dataAccess === false"
          type="error"
          :closable="false"
          show-icon
          :title="detail.accessMessage || '当前用户角色没有该工单的数据权限，字段已隐藏且不能审批；授权后请刷新页面'"
          style="margin-bottom: 16px"
        />
        <el-form v-if="detail.resubmitTask" label-width="100px" class="resubmit-form">
          <el-alert
            title="该申请已被驳回，请修改表单后重新提交"
            type="warning"
            :closable="false"
            show-icon
            style="margin-bottom: 16px"
          />
          <el-form-item v-for="f in detail.formSchema || []" :key="f.field" :label="f.title">
            <el-input v-if="f.type === 'input' || !f.type" v-model="editFormData[f.field]" />
            <el-input v-else-if="f.type === 'textarea'" v-model="editFormData[f.field]" type="textarea" />
            <el-input-number v-else-if="f.type === 'number'" v-model="editFormData[f.field]" />
            <el-select v-else-if="f.type === 'select'" v-model="editFormData[f.field]" style="width: 100%">
              <el-option v-for="o in f.options || []" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
            <el-select
              v-else-if="f.type === 'user' || f.type === 'users'"
              v-model="editFormData[f.field]"
              :multiple="f.type === 'users'"
              filterable
              style="width: 100%"
            >
              <el-option
                v-for="u in users"
                :key="u.id"
                :label="`${u.realName || u.username}（${u.username}）`"
                :value="String(u.id)"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <pre v-else class="form-box">{{ prettyForm }}</pre>
        <el-form label-width="80px" style="margin-top: 16px">
          <el-form-item :label="detail.resubmitTask ? '提交说明' : '意见'">
            <el-input v-model="comment" type="textarea" :rows="3" :disabled="detail.dataAccess === false" placeholder="请输入审批意见" />
          </el-form-item>
          <el-form-item v-if="!detail.resubmitTask && !detail.addSignTask" label="抄送">
            <el-select v-model="ccUserIds" multiple filterable remote :remote-method="searchUsers" placeholder="选择抄送人" style="width: 100%">
              <el-option v-for="u in users" :key="u.id" :label="`${u.realName} (${u.username})`" :value="u.id" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :disabled="detail.dataAccess === false" @click="approve">
              {{ detail.resubmitTask ? '重新提交' : detail.addSignTask ? '完成加签' : '同意' }}
            </el-button>
            <template v-if="!detail.resubmitTask && !detail.addSignTask">
              <el-button type="danger" :disabled="detail.dataAccess === false" @click="openReject">驳回</el-button>
              <el-button :disabled="detail.dataAccess === false" @click="openTransfer">转办</el-button>
              <el-button :disabled="detail.dataAccess === false" @click="openAddSign">加签</el-button>
            </template>
          </el-form-item>
        </el-form>
      </el-col>
      <el-col :span="10">
        <h3>审批轨迹</h3>
        <ApprovalTimeline :data="timeline" />
      </el-col>
    </el-row>

    <el-dialog v-model="rejectVisible" title="驳回" width="520px">
      <el-form label-width="90px">
        <el-form-item label="驳回方式">
          <el-radio-group v-model="rejectMode">
            <el-radio label="ACTIVITY">回退到指定节点</el-radio>
            <el-radio label="TERMINATE">直接终止流程</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="rejectMode === 'ACTIVITY'" label="回退到">
          <el-select
            v-model="rejectToActivityId"
            placeholder="选择要退回的节点"
            style="width: 100%"
            :loading="targetsLoading"
          >
            <el-option
              v-for="t in rejectTargets"
              :key="t.activityId"
              :label="t.systemNode || !t.starterNode ? t.activityName : `${t.activityName}（发起人）`"
              :value="t.activityId"
            />
          </el-select>
          <div v-if="!targetsLoading && !rejectTargets.length" class="hint">
            该任务前面没有已完成的审批节点，只能选择直接终止。
          </div>
          <div v-else class="hint">
            回退后流程不会结束，目标节点的处理人会重新收到待办，处理完继续按原路径往下走。
          </div>
        </el-form-item>
        <el-form-item v-else label="说明">
          <div class="hint">终止后流程实例结束，状态变为「已驳回」，发起人需要重新发起一次申请。</div>
        </el-form-item>
        <el-form-item label="驳回意见" required>
          <el-input v-model="comment" type="textarea" :rows="3" placeholder="请填写驳回原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejecting" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="transferVisible" title="转办" width="420px">
      <el-select v-model="transferUserId" filterable remote :remote-method="searchUsers" placeholder="选择转办人" style="width: 100%">
        <el-option v-for="u in users" :key="u.id" :label="`${u.realName} (${u.username})`" :value="String(u.id)" />
      </el-select>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" @click="transfer">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="addSignVisible" title="前加签" width="460px">
      <el-select
        v-model="addSignUserIds"
        multiple
        filterable
        remote
        collapse-tags
        collapse-tags-tooltip
        :remote-method="searchUsers"
        placeholder="选择一个或多个加签人"
        style="width: 100%"
      >
        <el-option v-for="u in users" :key="u.id" :label="`${u.realName || u.username}（${u.username}）`" :value="String(u.id)" />
      </el-select>
      <el-input v-model="comment" type="textarea" :rows="3" placeholder="加签说明（选填）" style="margin-top: 12px" />
      <div class="hint">全部加签人完成后，任务自动返回当前审批人。</div>
      <template #footer>
        <el-button @click="addSignVisible = false">取消</el-button>
        <el-button type="primary" @click="addSign">确定加签</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/utils/http'
import ApprovalTimeline from '@/components/ApprovalTimeline.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref<any>({})
const timeline = ref<any>({ nodes: [] })
const comment = ref('')
const ccUserIds = ref<number[]>([])
const users = ref<any[]>([])
const transferVisible = ref(false)
const transferUserId = ref('')
const addSignVisible = ref(false)
const addSignUserIds = ref<string[]>([])
const rejectVisible = ref(false)
const rejecting = ref(false)
const rejectMode = ref<'ACTIVITY' | 'TERMINATE'>('ACTIVITY')
const rejectToActivityId = ref('')
const rejectTargets = ref<any[]>([])
const targetsLoading = ref(false)
const editFormData = ref<Record<string, any>>({})

const prettyForm = computed(() => {
  try {
    return JSON.stringify(JSON.parse(detail.value.formData || '{}'), null, 2)
  } catch {
    return detail.value.formData || '{}'
  }
})

async function load() {
  loading.value = true
  try {
    const res: any = await http.get(`/runtime/tasks/${route.params.taskId}`)
    detail.value = res.data || {}
    // 工单类待办统一到工单详情办理，那里会按节点字段权限展示完整表单。
    if (detail.value.businessType === 'TICKET' && detail.value.processInstanceId) {
      const ticket: any = await http.get(
        `/ticket/tickets/by-process/${detail.value.processInstanceId}`,
      )
      if (ticket.data?.id && ticket.data?.typeCode) {
        await router.replace(`/tickets/${ticket.data.typeCode}/${ticket.data.id}`)
        return
      }
    }
    try {
      editFormData.value = JSON.parse(detail.value.formData || '{}')
    } catch {
      editFormData.value = {}
    }
    if (detail.value.resubmitTask && (detail.value.formSchema || []).some((f: any) => f.type === 'user' || f.type === 'users')) {
      await searchUsers('')
    }
    if (detail.value.processInstanceId) {
      const tl: any = await http.get(`/runtime/timeline/${detail.value.processInstanceId}`)
      timeline.value = tl.data || { nodes: [] }
    }
  } finally {
    loading.value = false
  }
}

async function searchUsers(q: string) {
  const res: any = await http.get('/system/users/simple', { params: { keyword: q } })
  const merged = [...users.value, ...(res.data || [])]
  users.value = merged.filter(
    (user, index) => merged.findIndex((item) => String(item.id) === String(user.id)) === index,
  )
}

async function approve() {
  await http.post('/runtime/approve', {
    taskId: route.params.taskId,
    comment: comment.value,
    ccUserIds: ccUserIds.value,
    formData: detail.value.resubmitTask ? editFormData.value : undefined,
  })
  ElMessage.success(detail.value.resubmitTask ? '已重新提交' : detail.value.addSignTask ? '加签已完成' : '已同意')
  router.push('/todo')
}

async function openReject() {
  rejectVisible.value = true
  targetsLoading.value = true
  try {
    const res: any = await http.get(`/runtime/tasks/${route.params.taskId}/reject-targets`)
    rejectTargets.value = res.data || []
    // 优先默认退回发起人节点，其次是上一个审批节点
    const starter = rejectTargets.value.find((t) => t.starterNode)
    const last = rejectTargets.value[rejectTargets.value.length - 1]
    rejectToActivityId.value = (starter || last)?.activityId || ''
    if (!rejectTargets.value.length) rejectMode.value = 'TERMINATE'
  } finally {
    targetsLoading.value = false
  }
}

async function submitReject() {
  if (!comment.value.trim()) {
    return ElMessage.warning('请填写驳回意见')
  }
  if (rejectMode.value === 'ACTIVITY' && !rejectToActivityId.value) {
    return ElMessage.warning('请选择要回退到的节点')
  }
  rejecting.value = true
  try {
    await http.post('/runtime/reject', {
      taskId: route.params.taskId,
      comment: comment.value,
      rejectMode: rejectMode.value,
      rejectToActivityId: rejectMode.value === 'ACTIVITY' ? rejectToActivityId.value : null,
    })
    ElMessage.success(rejectMode.value === 'ACTIVITY' ? '已驳回并回退' : '已驳回并终止')
    rejectVisible.value = false
    router.push('/todo')
  } finally {
    rejecting.value = false
  }
}

function openTransfer() {
  transferVisible.value = true
  searchUsers('')
}

async function transfer() {
  if (!transferUserId.value) return ElMessage.warning('请选择转办人')
  await http.post('/runtime/transfer', {
    taskId: route.params.taskId,
    transferUserId: transferUserId.value,
    comment: comment.value,
  })
  ElMessage.success('已转办')
  router.push('/todo')
}

function openAddSign() {
  addSignUserIds.value = []
  comment.value = ''
  addSignVisible.value = true
  searchUsers('')
}

async function addSign() {
  if (!addSignUserIds.value.length) return ElMessage.warning('请选择加签人')
  await http.post('/runtime/add-sign', {
    taskId: route.params.taskId,
    addSignUserIds: addSignUserIds.value,
    comment: comment.value,
  })
  ElMessage.success('加签任务已发送')
  router.push('/todo')
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
.hint {
  color: #7a8c85;
  font-size: 12px;
  line-height: 1.6;
  margin-top: 4px;
}
</style>
