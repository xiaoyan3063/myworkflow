<template>
  <div class="page-card">
    <div class="head">
      <div>
        <h1 class="page-title">角色管理</h1>
        <p class="page-sub">角色编码可用于流程节点审批人配置</p>
      </div>
      <el-button type="primary" @click="openEdit()">新建角色</el-button>
    </div>
    <el-table :data="list">
      <el-table-column prop="roleCode" label="编码" width="160" />
      <el-table-column prop="roleName" label="名称" min-width="160" />
      <el-table-column prop="sortNo" label="排序" width="90" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">{{ row.status === 1 ? '启用' : '停用' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" title="角色" width="420px">
      <el-form label-width="80px">
        <el-form-item label="编码"><el-input v-model="form.roleCode" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.roleName" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortNo" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/utils/http'

const list = ref<any[]>([])
const visible = ref(false)
const form = reactive<any>({})

async function load() {
  const res: any = await http.get('/system/roles')
  list.value = res.data || []
}

function openEdit(row?: any) {
  Object.assign(form, row ? { ...row } : { id: null, roleCode: '', roleName: '', sortNo: 0, status: 1, remark: '' })
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

onMounted(load)
</script>
<style scoped>.head{display:flex;justify-content:space-between;align-items:flex-start}</style>
