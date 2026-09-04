<template>
  <div class="page-card">
    <div class="head">
      <div>
        <h1 class="page-title">工单类型</h1>
        <p class="page-sub">维护工单分类；表单 / 列表 / 详情分别配置</p>
      </div>
      <el-button type="primary" @click="openType()">新建类型</el-button>
    </div>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="typeName" label="名称" min-width="140" />
      <el-table-column prop="typeCode" label="编码" width="140" />
      <el-table-column prop="processKey" label="绑定流程" width="160">
        <template #default="{ row }">{{ row.processKey || '—' }}</template>
      </el-table-column>
      <el-table-column label="编号规则" min-width="180">
        <template #default="{ row }">{{ noRuleText(row) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">{{ row.status === 1 ? '启用' : '停用' }}</template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="180" />
      <el-table-column label="操作" width="590" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openType(row)">编辑</el-button>
          <el-button link type="primary" @click="$router.push(`/ticket-types/${row.id}/form`)">设计表单</el-button>
          <el-button link type="primary" @click="$router.push(`/ticket-types/${row.id}/list`)">配置列表</el-button>
          <el-button link type="primary" @click="$router.push(`/ticket-types/${row.id}/detail`)">配置详情</el-button>
          <el-button link type="primary" @click="openRelations(row)">明细关系</el-button>
          <el-button link type="primary" @click="$router.push(`/tickets/${row.typeCode}`)">打开列表</el-button>
          <el-button link type="danger" @click="removeType(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination background layout="total, prev, pager, next" :total="total" v-model:current-page="page" @current-change="load" />
    </div>

    <el-dialog v-model="typeVisible" :title="typeForm.id ? '编辑类型' : '新建类型'" width="520px">
      <el-form label-width="100px">
        <el-form-item label="名称"><el-input v-model="typeForm.typeName" /></el-form-item>
        <el-form-item label="编码">
          <el-input v-model="typeForm.typeCode" :disabled="!!typeForm.id" placeholder="如 LEAVE，用于工单号前缀" />
        </el-form-item>
        <el-form-item label="绑定流程">
          <el-select v-model="typeForm.processKey" filterable clearable placeholder="必须选已发布流程" style="width: 100%">
            <el-option v-for="p in published" :key="p.processKey" :label="`${p.processName} (${p.processKey})`" :value="p.processKey" />
          </el-select>
        </el-form-item>
        <el-form-item label="编号前缀">
          <el-input v-model="typeForm.noPrefix" placeholder="空则用类型编码" />
        </el-form-item>
        <el-form-item label="日期格式">
          <el-select v-model="typeForm.noDatePattern" style="width: 100%">
            <el-option label="yyyyMMdd" value="yyyyMMdd" />
            <el-option label="yyyyMM" value="yyyyMM" />
            <el-option label="yyMMdd" value="yyMMdd" />
            <el-option label="yyyy-MM-dd" value="yyyy-MM-dd" />
          </el-select>
        </el-form-item>
        <el-form-item label="流水位数">
          <el-input-number v-model="typeForm.noSeqLen" :min="1" :max="8" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="typeForm.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeVisible = false">取消</el-button>
        <el-button type="primary" @click="saveType">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="relationVisible" :title="`${relationParent.typeName || ''} · 明细关系`" width="980px">
      <el-table :data="relations" border>
        <el-table-column prop="relationName" label="显示名称" min-width="130" />
        <el-table-column prop="relationCode" label="关系编码" min-width="120" />
        <el-table-column prop="childTypeName" label="明细类型" min-width="140" />
        <el-table-column label="删主单时" width="120">
          <template #default="{ row }">{{ row.cascadeDelete === 1 ? '级联删除' : '保留并解绑' }}</template>
        </el-table-column>
        <el-table-column label="条数" min-width="140">
          <template #default="{ row }">{{ rowsText(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">{{ row.status === 1 ? '启用' : '停用' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="editRelation(row)">编辑</el-button>
            <el-button link type="danger" @click="removeRelation(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-divider content-position="left">{{ relationForm.id ? '编辑关系' : '新增关系' }}</el-divider>
      <el-form :model="relationForm" inline label-width="76px">
        <el-form-item label="显示名称"><el-input v-model="relationForm.relationName" style="width: 150px" /></el-form-item>
        <el-form-item label="关系编码"><el-input v-model="relationForm.relationCode" :disabled="!!relationForm.id" style="width: 140px" /></el-form-item>
        <el-form-item label="明细类型">
          <el-select v-model="relationForm.childTypeId" filterable style="width: 170px">
            <el-option v-for="t in allTypes.filter(t => t.id !== relationParent.id)" :key="t.id" :label="t.typeName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="级联删除"><el-switch v-model="relationForm.cascadeDelete" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="条数范围">
          <span class="rows-label">下限</span>
          <el-input-number v-model="relationForm.minRows" :min="0" :controls="false" style="width: 70px" />
          <span class="rows-label">上限</span>
          <el-input-number v-model="relationForm.maxRows" :min="0" :controls="false" style="width: 70px" />
          <span class="rows-hint">0 表示不限。至少 1 条填下限 1、上限 0</span>
        </el-form-item>
        <el-form-item label="发起校验">
          <el-switch v-model="relationForm.checkMinOnStart" :active-value="1" :inactive-value="0" :disabled="!(relationForm.minRows > 0)" />
          <span class="rows-hint">勾选后主单提交时也拦截下限；不勾则只在审批节点同意时拦截</span>
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="relationForm.status" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item><el-button type="primary" @click="saveRelation">保存关系</el-button></el-form-item>
      </el-form>
      <template #footer><el-button @click="relationVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/utils/http'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const typeVisible = ref(false)
const typeForm = reactive<any>({})
const published = ref<any[]>([])
const relationVisible = ref(false)
const relationParent = reactive<any>({})
const relationForm = reactive<any>({})
const relations = ref<any[]>([])
const allTypes = ref<any[]>([])

function noRuleText(row: any) {
  const prefix = row.noPrefix || row.typeCode || ''
  const date = row.noDatePattern || 'yyyyMMdd'
  const seq = row.noSeqLen || 4
  return `${prefix}-${date}-${'0'.repeat(Math.max(0, seq - 1))}1`
}

async function load() {
  loading.value = true
  try {
    const res: any = await http.get('/ticket/types', { params: { page: page.value, size: 10 } })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

function openType(row?: any) {
  Object.keys(typeForm).forEach(k => delete typeForm[k])
  Object.assign(typeForm, row ? { ...row } : { status: 1, noDatePattern: 'yyyyMMdd', noSeqLen: 4 })
  if (!typeForm.noDatePattern) typeForm.noDatePattern = 'yyyyMMdd'
  if (!typeForm.noSeqLen) typeForm.noSeqLen = 4
  typeVisible.value = true
}

async function saveType() {
  if (!typeForm.typeName || !typeForm.typeCode) return ElMessage.warning('请填写名称和编码')
  await http.post('/ticket/types', typeForm)
  ElMessage.success('已保存')
  typeVisible.value = false
  load()
  userStore.fetchMe().catch(() => undefined)
}

async function removeType(row: any) {
  await ElMessageBox.confirm(`删除类型「${row.typeName}」？`, '确认')
  await http.delete(`/ticket/types/${row.id}`)
  ElMessage.success('已删除')
  load()
  userStore.fetchMe().catch(() => undefined)
}

function resetRelation() {
  Object.keys(relationForm).forEach(k => delete relationForm[k])
  Object.assign(relationForm, {
    cascadeDelete: 1,
    status: 1,
    minRows: 0,
    maxRows: 0,
    checkMinOnStart: 0,
    sortNo: relations.value.length,
  })
}

/** 0 表示不限，展示成「不限 / 至少 n / 最多 n / n~m」 */
function rowsText(row: any) {
  const min = Number(row.minRows) || 0
  const max = Number(row.maxRows) || 0
  let text = '不限'
  if (min && !max) text = `至少 ${min}`
  else if (!min && max) text = `最多 ${max}`
  else if (min && max) text = `${min} ~ ${max}`
  if (min && row.checkMinOnStart === 1) text += '（发起时）'
  return text
}

async function loadRelations() {
  const res: any = await http.get(`/ticket/types/${relationParent.id}/relations`)
  relations.value = res.data || []
}

async function openRelations(row: any) {
  Object.assign(relationParent, row)
  resetRelation()
  const typeRes: any = await http.get('/ticket/types/enabled')
  allTypes.value = typeRes.data || []
  await loadRelations()
  relationVisible.value = true
}

function editRelation(row: any) {
  Object.keys(relationForm).forEach(k => delete relationForm[k])
  Object.assign(relationForm, { ...row })
}

async function saveRelation() {
  if (!relationForm.relationName || !relationForm.relationCode || !relationForm.childTypeId) {
    return ElMessage.warning('请填写显示名称、关系编码和明细类型')
  }
  const min = Number(relationForm.minRows) || 0
  const max = Number(relationForm.maxRows) || 0
  if (max > 0 && min > max) return ElMessage.warning('条数下限不能大于上限')
  if (relationForm.checkMinOnStart === 1 && min <= 0) {
    return ElMessage.warning('发起时校验下限需要先设置大于 0 的下限')
  }
  await http.post(`/ticket/types/${relationParent.id}/relations`, relationForm)
  ElMessage.success('关系已保存')
  resetRelation()
  await loadRelations()
}

async function removeRelation(row: any) {
  await ElMessageBox.confirm(`删除明细关系「${row.relationName}」？`, '确认')
  await http.delete(`/ticket/types/${relationParent.id}/relations/${row.id}`)
  ElMessage.success('已删除')
  await loadRelations()
}

onMounted(async () => {
  load()
  try {
    const res: any = await http.get('/process/defs/published')
    published.value = res.data || []
  } catch {
    published.value = []
  }
})
</script>
<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
.rows-label { margin: 0 6px 0 4px; color: var(--el-text-color-regular); }
.rows-hint { margin-left: 8px; font-size: 12px; color: #7a8a84; }
</style>
