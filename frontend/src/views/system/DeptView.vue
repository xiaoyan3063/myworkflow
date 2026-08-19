<template>
  <div class="page-card">
    <div class="head">
      <div>
        <h1 class="page-title">部门管理</h1>
        <p class="page-sub">组织树，用于按部门解析审批人</p>
      </div>
      <el-button type="primary" @click="openEdit()">新建部门</el-button>
    </div>
    <el-tree :data="tree" node-key="id" default-expand-all :props="{ label: 'label', children: 'children' }">
      <template #default="{ data }">
        <div class="node">
          <span>{{ data.label }} <small>#{{ data.id }}</small></span>
          <span>
            <el-button link type="primary" @click.stop="openEdit({ parentId: data.id })">子部门</el-button>
            <el-button link @click.stop="openEdit(data)">编辑</el-button>
            <el-button link type="danger" @click.stop="remove(data.id)">删除</el-button>
          </span>
        </div>
      </template>
    </el-tree>

    <el-dialog v-model="visible" title="部门" width="420px">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.deptName" /></el-form-item>
        <el-form-item label="编码"><el-input v-model="form.deptCode" /></el-form-item>
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            node-key="value"
            check-strictly
            filterable
            :render-after-expand="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortNo" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/utils/http'

const tree = ref<any[]>([])
const visible = ref(false)
const form = reactive<any>({})

/** 上级部门候选：顶级 + 现有部门树，编辑时排除自身及其子孙，避免形成环 */
const parentOptions = computed(() => {
  const strip = (nodes: any[]): any[] =>
    nodes
      .filter((n) => String(n.id) !== String(form.id))
      .map((n) => ({
        value: String(n.id),
        label: n.label,
        children: n.children?.length ? strip(n.children) : undefined,
      }))
  return [{ value: '0', label: '顶级部门' }, ...strip(tree.value)]
})

async function load() {
  const res: any = await http.get('/system/depts/tree')
  tree.value = res.data || []
}

function openEdit(row?: any) {
  Object.assign(form, row?.id
    ? { id: row.id, deptName: row.deptName || row.label, deptCode: row.deptCode, parentId: String(row.parentId ?? 0), sortNo: row.sortNo || 0, status: 1 }
    : { id: null, deptName: '', deptCode: '', parentId: String(row?.parentId ?? 0), sortNo: 0, status: 1 })
  visible.value = true
}

async function save() {
  await http.post('/system/depts', form)
  ElMessage.success('保存成功')
  visible.value = false
  load()
}

async function remove(id: string) {
  try {
    await ElMessageBox.confirm('确认删除该部门？', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await http.delete(`/system/depts/${id}`)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
<style scoped>
.head{display:flex;justify-content:space-between;align-items:flex-start}
.node{flex:1;display:flex;justify-content:space-between;align-items:center;padding-right:8px}
.node small{color:#999;margin-left:6px}
</style>
