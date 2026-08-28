<template>
  <div class="page-card">
    <div class="head">
      <div>
        <h1 class="page-title">审批角色</h1>
        <p class="page-sub">审批角色只决定流程办理人，不授予菜单或工单数据权限</p>
      </div>
      <el-button type="primary" @click="openEdit()">新建审批角色</el-button>
    </div>

    <el-table :data="list">
      <el-table-column prop="roleCode" label="编码" min-width="150" />
      <el-table-column prop="roleName" label="名称" min-width="150" />
      <el-table-column prop="sortNo" label="排序" width="90" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">{{ row.status === 1 ? '启用' : '停用' }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="openMembers(row)">成员</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="editVisible" title="审批角色" width="500px">
      <el-form label-width="90px">
        <el-form-item label="编码" required>
          <el-input v-model="form.roleCode" :disabled="!!form.id" placeholder="如 EVENT_MANAGER" />
        </el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.roleName" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortNo" :min="0" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="memberVisible" :title="`${currentRole?.roleName || ''} - 成员`" width="720px">
      <div class="member-add">
        <el-select
          v-model="selectedUserIds"
          multiple
          collapse-tags
          collapse-tags-tooltip
          filterable
          remote
          reserve-keyword
          clearable
          :remote-method="searchUsers"
          :loading="userLoading"
          placeholder="输入账号或姓名，从系统用户中选择"
          style="width: 420px"
        >
          <el-option
            v-for="u in userOptions"
            :key="u.id"
            :label="`${u.realName || u.username}（${u.username}）`"
            :value="String(u.id)"
          />
        </el-select>
        <el-button type="primary" :disabled="!selectedUserIds.length" @click="addMembers">增加用户</el-button>
      </div>
      <el-table :data="members" max-height="420">
        <el-table-column prop="username" label="账号" min-width="130" />
        <el-table-column prop="realName" label="姓名" min-width="120" />
        <el-table-column label="部门" min-width="150">
          <template #default="{ row }">{{ deptNames[String(row.deptId)] || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">{{ row.status === 1 ? '启用' : '停用' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="danger" @click="members = members.filter((x) => String(x.id) !== String(row.id))">
              移除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="memberVisible = false">取消</el-button>
        <el-button type="primary" @click="saveMembers">保存成员</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/utils/http'

const list = ref<any[]>([])
const editVisible = ref(false)
const memberVisible = ref(false)
const form = reactive<any>({})
const currentRole = ref<any>()
const members = ref<any[]>([])
const selectedUserIds = ref<string[]>([])
const userOptions = ref<any[]>([])
const userLoading = ref(false)
const deptNames = reactive<Record<string, string>>({})

async function load() {
  const res: any = await http.get('/process/approval-roles')
  list.value = res.data || []
}

function openEdit(row?: any) {
  Object.assign(form, row
    ? { ...row }
    : { id: null, roleCode: '', roleName: '', sortNo: 0, status: 1, remark: '' })
  editVisible.value = true
}

async function save() {
  if (!form.roleCode?.trim() || !form.roleName?.trim()) {
    ElMessage.warning('请填写审批角色编码和名称')
    return
  }
  await http.post('/process/approval-roles', form)
  ElMessage.success('保存成功')
  editVisible.value = false
  load()
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除审批角色“${row.roleName}”？已发布流程中的角色编码不会自动替换。`, '确认删除')
  await http.delete(`/process/approval-roles/${row.id}`)
  ElMessage.success('已删除')
  load()
}

async function openMembers(row: any) {
  currentRole.value = row
  const res: any = await http.get(`/process/approval-roles/${row.id}/users`)
  members.value = res.data || []
  selectedUserIds.value = []
  userOptions.value = []
  memberVisible.value = true
}

async function searchUsers(keyword: string) {
  userLoading.value = true
  try {
    const res: any = await http.get('/system/users/simple', { params: { keyword } })
    const merged = [...userOptions.value, ...(res.data || [])]
    userOptions.value = merged.filter(
      (user, index) => merged.findIndex((item) => String(item.id) === String(user.id)) === index,
    )
  } finally {
    userLoading.value = false
  }
}

function addMembers() {
  for (const userId of selectedUserIds.value) {
    const user = userOptions.value.find((x) => String(x.id) === userId)
    if (user && !members.value.some((x) => String(x.id) === String(user.id))) {
      members.value.push({ ...user, status: 1 })
    }
  }
  selectedUserIds.value = []
}

async function saveMembers() {
  await http.post(`/process/approval-roles/${currentRole.value.id}/users`, members.value.map((x) => x.id))
  ElMessage.success('成员已保存')
  memberVisible.value = false
}

function collectDeptNames(nodes: any[]) {
  for (const node of nodes || []) {
    deptNames[String(node.id)] = node.deptName || node.label
    collectDeptNames(node.children)
  }
}

onMounted(async () => {
  await load()
  const res: any = await http.get('/system/depts/tree')
  collectDeptNames(res.data || [])
})
</script>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.member-add { display: flex; gap: 10px; margin-bottom: 16px; }
</style>
