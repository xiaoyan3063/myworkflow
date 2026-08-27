<template>
  <div class="page-card">
    <div class="head">
      <div>
        <h1 class="page-title">系统日志</h1>
        <p class="page-sub">记录用户登录及系统写操作，敏感参数已自动脱敏</p>
      </div>
      <div v-if="isAdmin" class="cleanup">
        <el-date-picker
          v-model="cleanupTime"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          placeholder="清理此时间以前"
        />
        <el-button type="danger" plain :disabled="!cleanupTime" @click="cleanup">清理历史日志</el-button>
      </div>
    </div>

    <el-tabs v-model="active" @tab-change="switchTab">
      <el-tab-pane label="登录日志" name="login">
        <el-form inline>
          <el-form-item label="账号"><el-input v-model="loginQuery.username" clearable /></el-form-item>
          <el-form-item label="结果">
            <el-select v-model="loginQuery.status" clearable style="width: 120px">
              <el-option label="成功" :value="1" /><el-option label="失败" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="searchLogin">查询</el-button>
            <el-button v-if="isAdmin" type="danger" plain :disabled="!loginSelected.length" @click="deleteLogin">
              删除所选
            </el-button>
          </el-form-item>
        </el-form>
        <el-table :data="loginList" v-loading="loading" @selection-change="loginSelected = $event">
          <el-table-column v-if="isAdmin" type="selection" width="48" />
          <el-table-column prop="username" label="账号" min-width="120" show-overflow-tooltip />
          <el-table-column label="姓名" min-width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ row.realName || '-' }}</template>
          </el-table-column>
          <el-table-column label="结果" min-width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'">
                {{ row.status === 1 ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="180" show-overflow-tooltip />
          <el-table-column prop="ip" label="IP" min-width="130" show-overflow-tooltip />
          <el-table-column label="客户端" min-width="100">
            <template #default="{ row }">
              <el-tooltip :content="row.userAgent || '未知客户端'" placement="top">
                <span>{{ clientText(row.clientType) }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="登录时间" min-width="180" />
        </el-table>
        <LogPager v-model="loginPage" :total="loginTotal" @change="loadLogin" />
      </el-tab-pane>

      <el-tab-pane label="操作日志" name="operation">
        <el-form inline>
          <el-form-item label="操作人"><el-input v-model="operQuery.username" clearable placeholder="账号或姓名" /></el-form-item>
          <el-form-item label="模块">
            <el-select v-model="operQuery.module" clearable style="width: 130px">
              <el-option v-for="m in modules" :key="m" :label="m" :value="m" />
            </el-select>
          </el-form-item>
          <el-form-item label="结果">
            <el-select v-model="operQuery.status" clearable style="width: 120px">
              <el-option label="成功" :value="1" /><el-option label="失败" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="searchOperation">查询</el-button>
            <el-button v-if="isAdmin" type="danger" plain :disabled="!operSelected.length" @click="deleteOperation">
              删除所选
            </el-button>
          </el-form-item>
        </el-form>
        <el-table :data="operList" v-loading="loading" @selection-change="operSelected = $event">
          <el-table-column v-if="isAdmin" type="selection" width="48" />
          <el-table-column prop="module" label="模块" min-width="100" />
          <el-table-column label="操作类型" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.ticketTypeName || row.module || '-' }}</template>
          </el-table-column>
          <el-table-column prop="title" label="操作" min-width="150" show-overflow-tooltip />
          <el-table-column label="操作人" min-width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ row.realName || '-' }}</template>
          </el-table-column>
          <el-table-column prop="username" label="账号" min-width="120" show-overflow-tooltip />
          <el-table-column label="结果" min-width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'">
                {{ row.status === 1 ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="httpMethod" label="方法" min-width="85" />
          <el-table-column prop="requestUri" label="地址" min-width="220" show-overflow-tooltip />
          <el-table-column prop="costMs" label="耗时(ms)" min-width="100" />
          <el-table-column prop="source" label="来源" min-width="90" />
          <el-table-column prop="createTime" label="操作时间" min-width="180" />
          <el-table-column label="详情" width="80" fixed="right">
            <template #default="{ row }"><el-button link type="primary" @click="showDetail(row)">查看</el-button></template>
          </el-table-column>
        </el-table>
        <LogPager v-model="operPage" :total="operTotal" @change="loadOperation" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="detailVisible" title="操作日志详情" width="680px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="操作">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="操作类型">{{ detail.ticketTypeName || detail.module || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detail.realName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="账号">{{ detail.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ detail.ip }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detail.costMs }} ms</el-descriptions-item>
        <el-descriptions-item label="请求地址" :span="2">{{ detail.requestUri }}</el-descriptions-item>
        <el-descriptions-item label="请求参数" :span="2"><pre>{{ detail.operParam || '-' }}</pre></el-descriptions-item>
        <el-descriptions-item v-if="detail.errorMsg" label="错误信息" :span="2">
          {{ detail.errorMsg }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, ElPagination } from 'element-plus'
import http from '@/utils/http'
import { useUserStore } from '@/stores/user'

const LogPager = defineComponent({
  props: { modelValue: { type: Number, required: true }, total: { type: Number, required: true } },
  emits: ['update:modelValue', 'change'],
  setup(props, { emit }) {
    return () => h('div', { class: 'pager' }, [
      h(ElPagination, {
        background: true, layout: 'total, prev, pager, next', total: props.total,
        currentPage: props.modelValue,
        'onUpdate:currentPage': (v: number) => emit('update:modelValue', v),
        onCurrentChange: () => emit('change'),
      }),
    ])
  },
})

const userStore = useUserStore()
const isAdmin = computed(() => !!userStore.profile?.admin)
const active = ref('login')
const loading = ref(false)
const loginList = ref<any[]>([])
const operList = ref<any[]>([])
const loginTotal = ref(0)
const operTotal = ref(0)
const loginPage = ref(1)
const operPage = ref(1)
const loginQuery = reactive<any>({})
const operQuery = reactive<any>({})
const loginSelected = ref<any[]>([])
const operSelected = ref<any[]>([])
const cleanupTime = ref('')
const modules = ['认证', '系统管理', '工单', '流程运行', '流程设计', '消息', '开放接口', '其他']
const detailVisible = ref(false)
const detail = ref<any>({})

async function loadLogin() {
  loading.value = true
  try {
    const res: any = await http.get('/system/logs/login', {
      params: { page: loginPage.value, size: 10, ...loginQuery },
    })
    loginList.value = res.data?.records || []
    loginTotal.value = res.data?.total || 0
  } finally { loading.value = false }
}
async function loadOperation() {
  loading.value = true
  try {
    const res: any = await http.get('/system/logs/operation', {
      params: { page: operPage.value, size: 10, ...operQuery },
    })
    operList.value = res.data?.records || []
    operTotal.value = res.data?.total || 0
  } finally { loading.value = false }
}
function searchLogin() { loginPage.value = 1; loadLogin() }
function searchOperation() { operPage.value = 1; loadOperation() }
function switchTab() { active.value === 'login' ? loadLogin() : loadOperation() }
function clientText(type?: string) {
  if (type === 'WEB') return 'CRM'
  if (type === 'APP') return 'APP'
  return '其他'
}
function showDetail(row: any) { detail.value = row; detailVisible.value = true }

async function deleteLogin() {
  await ElMessageBox.confirm(`删除选中的 ${loginSelected.value.length} 条登录日志？`, '确认删除')
  await http.delete('/system/logs/login', { data: { ids: loginSelected.value.map((x) => x.id) } })
  ElMessage.success('已删除'); loadLogin()
}
async function deleteOperation() {
  await ElMessageBox.confirm(`删除选中的 ${operSelected.value.length} 条操作日志？`, '确认删除')
  await http.delete('/system/logs/operation', { data: { ids: operSelected.value.map((x) => x.id) } })
  ElMessage.success('已删除'); loadOperation()
}
async function cleanup() {
  await ElMessageBox.confirm(`清理 ${cleanupTime.value.replace('T', ' ')} 以前的全部系统日志？`, '高风险操作', {
    type: 'warning',
  })
  await http.delete('/system/logs/before', { params: { before: cleanupTime.value } })
  ElMessage.success('历史日志已清理')
  active.value === 'login' ? loadLogin() : loadOperation()
}

onMounted(loadLogin)
</script>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.cleanup { display: flex; gap: 8px; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
pre { margin: 0; white-space: pre-wrap; word-break: break-all; max-height: 280px; overflow: auto; }
</style>
