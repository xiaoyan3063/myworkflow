<template>
  <div class="page-card">
    <div class="head">
      <div>
        <h1 class="page-title">列表配置</h1>
        <p class="page-sub">{{ typeName }} · 勾选列和筛选，保存到 tk_list_ui</p>
      </div>
      <div>
        <el-button @click="$router.push(`/tickets/${typeCode}`)" :disabled="!typeCode">打开列表</el-button>
        <el-button @click="$router.push('/ticket-types')">返回</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </div>
    </div>

    <p class="hint">
      主表字段固定为工单号 / 标题 / 状态 / 创建时间，其余来自 tk_field。
      拖动左侧手柄调整顺序，勾选「作列」的行按此顺序显示。
    </p>

    <div class="grid" v-loading="loading">
      <div class="row head-row">
        <span></span>
        <span>来源</span>
        <span>字段</span>
        <span>标题</span>
        <span class="center">作列</span>
        <span>列宽</span>
        <span class="center">作筛选</span>
        <span>筛选方式</span>
      </div>

      <div
        v-for="(row, i) in catalog"
        :key="row.field"
        class="row"
        :class="{ dragging: dragIndex === i, over: overIndex === i && dragIndex !== i, off: !row.asColumn }"
        draggable="true"
        @dragstart="onDragStart(i)"
        @dragover.prevent="onDragOver(i)"
        @drop.prevent="onDrop(i)"
        @dragend="onDragEnd"
      >
        <span class="handle" title="拖动调整顺序">⠿</span>
        <span>
          <el-tag size="small" :type="row.from === 'main' ? 'success' : 'info'">
            {{ row.from === 'main' ? '主表' : '扩展' }}
          </el-tag>
        </span>
        <span class="mono">{{ row.field }}</span>
        <span class="ellipsis" :title="row.title">{{ row.title }}</span>
        <span class="center"><el-checkbox v-model="row.asColumn" /></span>
        <span>
          <el-input-number
            v-model="row.width"
            :min="80"
            :max="480"
            :step="10"
            size="small"
            controls-position="right"
            :disabled="!row.asColumn"
            style="width: 110px"
          />
        </span>
        <span class="center"><el-checkbox v-model="row.asFilter" /></span>
        <span>
          <el-select v-model="row.op" size="small" :disabled="!row.asFilter" style="width: 150px">
            <el-option label="等于 eq" value="eq" />
            <el-option label="包含 like" value="like" />
            <el-option label="大于 gt" value="gt" />
            <el-option label="大于等于 gte" value="gte" />
            <el-option label="小于 lt" value="lt" />
            <el-option label="小于等于 lte" value="lte" />
          </el-select>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/utils/http'

const MAIN = [
  { field: 'ticket_no', title: '工单号', from: 'main' as const, op: 'like', width: 180 },
  { field: 'title', title: '标题', from: 'main' as const, op: 'like', width: 200 },
  { field: 'status', title: '状态', from: 'main' as const, op: 'eq', width: 100 },
  { field: 'createTime', title: '创建时间', from: 'main' as const, op: 'gte', width: 180 },
]

const route = useRoute()
const typeId = route.params.id as string
const typeName = ref('')
const typeCode = ref('')
const loading = ref(false)
const saving = ref(false)
const catalog = ref<any[]>([])
const dragIndex = ref(-1)
const overIndex = ref(-1)

function defaultOp(field: string, fieldType?: string) {
  if (field === 'status') return 'eq'
  if (field === 'createTime') return 'gte'
  if (fieldType === 'number' || fieldType === 'date') return 'gte'
  if (fieldType === 'select') return 'eq'
  return 'like'
}

function onDragStart(i: number) {
  dragIndex.value = i
}

function onDragOver(i: number) {
  overIndex.value = i
}

function onDrop(to: number) {
  const from = dragIndex.value
  if (from < 0 || from === to) return
  const next = [...catalog.value]
  const [moved] = next.splice(from, 1)
  next.splice(to, 0, moved)
  catalog.value = next
  onDragEnd()
}

function onDragEnd() {
  dragIndex.value = -1
  overIndex.value = -1
}

onMounted(async () => {
  loading.value = true
  try {
    const t: any = await http.get(`/ticket/types/${typeId}`)
    typeName.value = t.data?.typeName || ''
    typeCode.value = t.data?.typeCode || ''
    const [fieldsRes, uiRes]: any[] = await Promise.all([
      http.get(`/ticket/types/${typeId}/fields`),
      http.get(`/ticket/types/${typeId}/list-ui`),
    ])
    const schema = uiRes.data?.schema || {}
    const savedColumns: any[] = schema.columns || []
    const colMap = new Map(savedColumns.map((c: any) => [c.field, c]))
    const filterMap = new Map((schema.filters || []).map((f: any) => [f.field, f]))
    const jsonRows = (fieldsRes.data || []).map((f: any) => ({
      field: f.fieldKey,
      title: f.title,
      from: 'json',
      fieldType: f.fieldType,
      asColumn: colMap.has(f.fieldKey),
      width: colMap.get(f.fieldKey)?.width || 140,
      asFilter: filterMap.has(f.fieldKey),
      op: filterMap.get(f.fieldKey)?.op || defaultOp(f.fieldKey, f.fieldType),
    }))
    const mainRows = MAIN.map((m) => ({
      ...m,
      asColumn: colMap.size ? colMap.has(m.field) : true,
      width: colMap.get(m.field)?.width || m.width,
      asFilter: filterMap.size ? filterMap.has(m.field) : (m.field === 'ticket_no' || m.field === 'status'),
      op: filterMap.get(m.field)?.op || m.op,
    }))
    // 已保存的列排在前面并保持配置顺序，未勾选的按主表、扩展依次排在后面
    const order = new Map(savedColumns.map((c: any, i: number) => [c.field, i]))
    catalog.value = [...mainRows, ...jsonRows].sort((a, b) => {
      const ai = order.has(a.field) ? (order.get(a.field) as number) : Number.MAX_SAFE_INTEGER
      const bi = order.has(b.field) ? (order.get(b.field) as number) : Number.MAX_SAFE_INTEGER
      return ai - bi
    })
  } finally {
    loading.value = false
  }
})

async function save() {
  const columns = catalog.value
    .filter((r) => r.asColumn)
    .map((r) => ({ field: r.field, title: r.title, width: r.width, from: r.from }))
  if (!columns.length) return ElMessage.warning('请至少勾选一列')
  const filters = catalog.value
    .filter((r) => r.asFilter)
    .map((r) => ({ field: r.field, op: r.op || 'eq', from: r.from }))
  saving.value = true
  try {
    await http.put(`/ticket/types/${typeId}/list-ui`, {
      columns,
      filters,
      rowActions: ['view', 'edit', 'submit', 'delete'],
    })
    ElMessage.success('已保存列表配置')
  } finally {
    saving.value = false
  }
}
</script>
<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.head > div:last-child { display: flex; gap: 8px; }
.page-title { margin: 0; }
.page-sub { margin: 4px 0 0; color: #7a8a84; }
.hint { margin: 0 0 12px; color: #7a8a84; font-size: 13px; }

.grid {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow-x: auto;
}
.row {
  display: grid;
  grid-template-columns: 36px 76px minmax(140px, 1fr) minmax(160px, 1.4fr) 64px 130px 72px 166px;
  align-items: center;
  gap: 8px;
  min-width: 940px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: #fff;
}
.row:last-child { border-bottom: none; }
.head-row {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 13px;
  position: sticky;
  top: 0;
}
.row.off { background: #fbfcfc; color: var(--el-text-color-secondary); }
.row.dragging { opacity: 0.45; }
.row.over { box-shadow: inset 0 2px 0 var(--el-color-primary); }
.handle {
  cursor: grab;
  color: #9aa7a2;
  font-size: 16px;
  user-select: none;
  text-align: center;
}
.handle:active { cursor: grabbing; }
.center { text-align: center; }
.mono { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 13px; word-break: break-all; }
.ellipsis { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
