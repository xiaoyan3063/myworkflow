<template>
  <span class="approval-actions">
    <el-button type="success" :loading="acting" @click="openApprove">
      {{ task.resubmitTask ? '重新提交' : '同意' }}
    </el-button>
    <template v-if="!task.resubmitTask">
      <el-button type="danger" @click="openReject">驳回</el-button>
      <el-button @click="openTransfer">转办</el-button>
    </template>

    <el-dialog v-model="approveVisible" :title="task.resubmitTask ? '重新提交' : '同意'" width="520px">
      <el-form label-width="80px">
        <el-form-item label="节点">
          <span class="node-name">{{ task.taskName }}</span>
        </el-form-item>
        <el-form-item :label="task.resubmitTask ? '提交说明' : '审批意见'">
          <el-input v-model="comment" type="textarea" :rows="3" placeholder="选填" />
        </el-form-item>
        <el-form-item v-if="!task.resubmitTask" label="抄送">
          <el-select
            v-model="ccUserIds"
            multiple
            filterable
            remote
            :remote-method="searchUsers"
            placeholder="选择抄送人"
            style="width: 100%"
          >
            <el-option v-for="u in users" :key="u.id" :label="`${u.realName}（${u.username}）`" :value="u.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="success" :loading="acting" @click="approve">确定</el-button>
      </template>
    </el-dialog>

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
          <div class="hint">终止后流程实例结束，工单变为「已驳回」，发起人需要重新提交一次。</div>
        </el-form-item>
        <el-form-item label="驳回意见" required>
          <el-input v-model="comment" type="textarea" :rows="3" placeholder="请填写驳回原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="acting" @click="reject">确认驳回</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="transferVisible" title="转办" width="420px">
      <el-select
        v-model="transferUserId"
        filterable
        remote
        :remote-method="searchUsers"
        placeholder="选择转办人"
        style="width: 100%"
      >
        <el-option v-for="u in users" :key="u.id" :label="`${u.realName}（${u.username}）`" :value="String(u.id)" />
      </el-select>
      <el-input v-model="comment" type="textarea" :rows="3" placeholder="转办说明（选填）" style="margin-top: 12px" />
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="transfer">确定</el-button>
      </template>
    </el-dialog>
  </span>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/utils/http'

const props = defineProps<{
  task: { taskId: string; taskName?: string; resubmitTask?: boolean }
  /** 办理前的钩子，工单详情用它先把表单存下来；抛异常则中断办理 */
  beforeAction?: () => Promise<void> | void
}>()
const emit = defineEmits<{ (e: 'done', action: 'approve' | 'reject' | 'transfer'): void }>()

const acting = ref(false)
const comment = ref('')
const users = ref<any[]>([])
const ccUserIds = ref<number[]>([])
const approveVisible = ref(false)
const rejectVisible = ref(false)
const transferVisible = ref(false)
const transferUserId = ref('')
const rejectMode = ref<'ACTIVITY' | 'TERMINATE'>('ACTIVITY')
const rejectToActivityId = ref('')
const rejectTargets = ref<any[]>([])
const targetsLoading = ref(false)

async function searchUsers(q: string) {
  const res: any = await http.get('/system/users/simple', { params: { keyword: q } })
  users.value = res.data || []
}

function openApprove() {
  comment.value = ''
  ccUserIds.value = []
  approveVisible.value = true
  if (!props.task.resubmitTask) searchUsers('')
}

async function approve() {
  acting.value = true
  try {
    try {
      await props.beforeAction?.()
    } catch {
      // 钩子（如节点必填校验、保存失败）已经给过提示，这里只中断办理
      return
    }
    await http.post('/runtime/approve', {
      taskId: props.task.taskId,
      comment: comment.value,
      ccUserIds: ccUserIds.value,
    })
    ElMessage.success(props.task.resubmitTask ? '已重新提交' : '已同意')
    approveVisible.value = false
    emit('done', 'approve')
  } finally {
    acting.value = false
  }
}

async function openReject() {
  comment.value = ''
  rejectMode.value = 'ACTIVITY'
  rejectVisible.value = true
  targetsLoading.value = true
  try {
    const res: any = await http.get(`/runtime/tasks/${props.task.taskId}/reject-targets`)
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

async function reject() {
  if (!comment.value.trim()) return ElMessage.warning('请填写驳回意见')
  if (rejectMode.value === 'ACTIVITY' && !rejectToActivityId.value) {
    return ElMessage.warning('请选择要回退到的节点')
  }
  acting.value = true
  try {
    await http.post('/runtime/reject', {
      taskId: props.task.taskId,
      comment: comment.value,
      rejectMode: rejectMode.value,
      rejectToActivityId: rejectMode.value === 'ACTIVITY' ? rejectToActivityId.value : null,
    })
    ElMessage.success(rejectMode.value === 'ACTIVITY' ? '已驳回并回退' : '已驳回并终止')
    rejectVisible.value = false
    emit('done', 'reject')
  } finally {
    acting.value = false
  }
}

function openTransfer() {
  comment.value = ''
  transferUserId.value = ''
  transferVisible.value = true
  searchUsers('')
}

async function transfer() {
  if (!transferUserId.value) return ElMessage.warning('请选择转办人')
  acting.value = true
  try {
    await http.post('/runtime/transfer', {
      taskId: props.task.taskId,
      transferUserId: transferUserId.value,
      comment: comment.value,
    })
    ElMessage.success('已转办')
    transferVisible.value = false
    emit('done', 'transfer')
  } finally {
    acting.value = false
  }
}
</script>

<style scoped>
.approval-actions { display: inline-flex; gap: 8px; }
.node-name { color: var(--el-text-color-regular); }
.hint {
  color: #7a8c85;
  font-size: 12px;
  line-height: 1.6;
  margin-top: 4px;
}
</style>
