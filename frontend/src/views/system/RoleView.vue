<template>
  <div class="page-card">
    <div class="head">
      <div>
        <h1 class="page-title">角色管理</h1>
        <p class="page-sub">勾选菜单；数据范围：全部 / 本部门（含下级、不含总部） / 仅自己</p>
      </div>
      <el-button type="primary" @click="openEdit()">新建角色</el-button>
    </div>
    <el-table :data="list">
      <el-table-column prop="roleCode" label="编码" width="160" />
      <el-table-column prop="roleName" label="名称" min-width="140" />
      <el-table-column prop="dataScope" label="数据范围" width="130">
        <template #default="{ row }">{{ scopeText(row.dataScope) }}</template>
      </el-table-column>
      <el-table-column prop="sortNo" label="排序" width="90" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">{{ row.status === 1 ? '启用' : '停用' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="openMenus(row)">菜单权限</el-button>
          <el-button link type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" title="角色" width="480px">
      <el-form label-width="90px">
        <el-form-item label="编码"><el-input v-model="form.roleCode" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.roleName" /></el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="form.dataScope" style="width: 100%">
            <el-option label="全部 ALL" value="ALL" />
            <el-option label="本部门 DEPT（含下级，不含上级/总部）" value="DEPT" />
            <el-option label="仅自己 SELF" value="SELF" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortNo" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="menuVisible" title="角色菜单" width="520px">
      <el-tree
        ref="treeRef"
        :data="menuTree"
        show-checkbox
        node-key="id"
        default-expand-all
        :props="{ label: 'menuName', children: 'children' }"
      />
      <template #footer>
        <el-button @click="menuVisible = false">取消</el-button>
        <el-button type="primary" @click="saveMenus">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/utils/http'

const list = ref<any[]>([])
const visible = ref(false)
const menuVisible = ref(false)
const form = reactive<any>({})
const menuTree = ref<any[]>([])
const treeRef = ref<any>()
const currentRoleId = ref('')

function scopeText(s?: string) {
  if (s === 'SELF') return '仅自己'
  if (s === 'DEPT') return '本部门'
  return '全部'
}

async function load() {
  const res: any = await http.get('/system/roles')
  list.value = res.data || []
}

function openEdit(row?: any) {
  Object.assign(form, row ? { ...row } : { id: null, roleCode: '', roleName: '', sortNo: 0, status: 1, dataScope: 'ALL', remark: '' })
  visible.value = true
}

async function save() {
  await http.post('/system/roles', form)
  ElMessage.success('保存成功')
  visible.value = false
  load()
}

async function remove(id: number) {
  await ElMessageBox.confirm('确认删除？')
  await http.delete(`/system/roles/${id}`)
  ElMessage.success('已删除')
  load()
}

async function openMenus(row: any) {
  currentRoleId.value = row.id
  const [tree, ids]: any[] = await Promise.all([
    http.get('/system/menus/tree'),
    http.get(`/system/roles/${row.id}/menus`),
  ])
  menuTree.value = tree.data || []
  menuVisible.value = true
  await nextTick()
  treeRef.value?.setCheckedKeys(ids.data || [])
}

async function saveMenus() {
  const keys = [
    ...(treeRef.value?.getCheckedKeys() || []),
    ...(treeRef.value?.getHalfCheckedKeys() || []),
  ]
  await http.post(`/system/roles/${currentRoleId.value}/menus`, keys)
  ElMessage.success('菜单已保存')
  menuVisible.value = false
}

onMounted(load)
</script>
<style scoped>.head{display:flex;justify-content:space-between;align-items:flex-start}</style>
