<template>
  <div class="page-card">
    <div class="head">
      <div>
        <h1 class="page-title">用户管理</h1>
        <p class="page-sub">组织内审批参与人</p>
      </div>
      <el-button type="primary" @click="openEdit()">新建用户</el-button>
    </div>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="username" label="用户名" width="120" />
      <el-table-column prop="realName" label="姓名" width="120" />
      <el-table-column label="部门" width="140">
        <template #default="{ row }">{{ deptNameOf(row.deptId) }}</template>
      </el-table-column>
      <el-table-column prop="mobile" label="手机" width="130" />
      <el-table-column prop="email" label="邮箱" min-width="160" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">{{ row.status === 1 ? '正常' : '停用' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link @click="openRoles(row)">角色</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '编辑用户' : '新建用户'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="用户名"><el-input v-model="form.username" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.realName" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" placeholder="不改请留空" /></el-form-item>
        <el-form-item label="部门">
          <el-tree-select
            v-model="form.deptId"
            :data="deptTree"
            node-key="value"
            check-strictly
            filterable
            :render-after-expand="false"
            placeholder="请选择部门"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="手机"><el-input v-model="form.mobile" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleVisible" title="分配角色" width="420px">
      <el-checkbox-group v-model="roleIds">
        <el-checkbox v-for="r in roles" :key="r.id" :label="r.id">{{ r.roleName }}</el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRoles">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/utils/http'

const list = ref<any[]>([])
const roles = ref<any[]>([])
const loading = ref(false)
const visible = ref(false)
const roleVisible = ref(false)
const currentUserId = ref<string>()
const roleIds = ref<string[]>([])
const deptTree = ref<any[]>([])
const deptMap = ref<Record<string, string>>({})
const form = reactive<any>({})

function toTree(nodes: any[]): any[] {
  return (nodes || []).map((n) => {
    deptMap.value[String(n.id)] = n.deptName || n.label
    return {
      value: String(n.id),
      label: n.deptName || n.label,
      children: n.children?.length ? toTree(n.children) : undefined,
    }
  })
}

function deptNameOf(id: any) {
  return deptMap.value[String(id)] || '-'
}

async function load() {
  loading.value = true
  try {
    const [res, deptRes]: any[] = await Promise.all([
      http.get('/system/users', { params: { page: 1, size: 50 } }),
      http.get('/system/depts/tree'),
    ])
    list.value = res.data?.records || []
    deptTree.value = toTree(deptRes.data || [])
  } finally { loading.value = false }
}

function openEdit(row?: any) {
  Object.assign(
    form,
    row
      ? { ...row, deptId: row.deptId == null ? null : String(row.deptId), password: '' }
      : { id: null, username: '', realName: '', password: 'admin123', deptId: null, status: 1 }
  )
  visible.value = true
}

async function save() {
  await http.post('/system/users', form)
  ElMessage.success('保存成功')
  visible.value = false
  load()
}

async function openRoles(row: any) {
  currentUserId.value = row.id
  const [all, mine]: any[] = await Promise.all([
    http.get('/system/roles'),
    http.get(`/system/users/${row.id}/roles`),
  ])
  roles.value = all.data || []
  roleIds.value = mine.data || []
  roleVisible.value = true
}

async function saveRoles() {
  await http.post(`/system/users/${currentUserId.value}/roles`, roleIds.value)
  ElMessage.success('角色已更新')
  roleVisible.value = false
}

onMounted(load)
</script>
<style scoped>.head{display:flex;justify-content:space-between;align-items:flex-start}</style>
