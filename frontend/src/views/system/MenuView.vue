<template>
  <div class="page-card">
    <div class="head">
      <div>
        <h1 class="page-title">菜单管理</h1>
        <p class="page-sub">DIR 目录 / MENU 页面 / BUTTON 按钮（填 perm 编码）</p>
      </div>
      <el-button type="primary" @click="openEdit()">新建菜单</el-button>
    </div>
    <el-table :data="tree" row-key="id" default-expand-all v-loading="loading">
      <el-table-column prop="menuName" label="名称" min-width="180" />
      <el-table-column label="图标" width="90">
        <template #default="{ row }">
          <el-icon v-if="row.icon && row.menuType !== 'BUTTON'"><component :is="row.icon" /></el-icon>
          <span v-else-if="row.menuType !== 'BUTTON'" class="muted">默认</span>
        </template>
      </el-table-column>
      <el-table-column prop="menuType" label="类型" width="90" />
      <el-table-column prop="path" label="路径" min-width="160" />
      <el-table-column prop="perm" label="权限编码" min-width="160" />
      <el-table-column prop="sortNo" label="排序" width="80" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(null, row.id)">新增子级</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '编辑菜单' : '新建菜单'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="类型">
          <el-select v-model="form.menuType" style="width: 100%">
            <el-option label="目录 DIR" value="DIR" />
            <el-option label="菜单 MENU" value="MENU" />
            <el-option label="按钮 BUTTON" value="BUTTON" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="form.menuName" /></el-form-item>
        <el-form-item label="路径"><el-input v-model="form.path" placeholder="如 /tickets/LEAVE" /></el-form-item>
        <el-form-item v-if="form.menuType !== 'BUTTON'" label="图标">
          <el-popover placement="bottom-start" :width="420" trigger="click">
            <template #reference>
              <el-button>
                <el-icon v-if="form.icon"><component :is="form.icon" /></el-icon>
                {{ form.icon || '选择图标' }}
              </el-button>
            </template>
            <MenuIconPicker v-model="form.icon" />
          </el-popover>
        </el-form-item>
        <el-form-item label="权限编码"><el-input v-model="form.perm" placeholder="如 ticket:submit" /></el-form-item>
        <el-form-item label="父级ID"><el-input v-model="form.parentId" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortNo" /></el-form-item>
        <el-form-item label="显示">
          <el-switch v-model="form.visible" :active-value="1" :inactive-value="0" />
        </el-form-item>
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
import MenuIconPicker from '@/components/MenuIconPicker.vue'

const tree = ref<any[]>([])
const loading = ref(false)
const visible = ref(false)
const form = reactive<any>({})

async function load() {
  loading.value = true
  try {
    const res: any = await http.get('/system/menus/tree')
    tree.value = res.data || []
  } finally {
    loading.value = false
  }
}

function openEdit(row?: any, parentId?: string) {
  Object.keys(form).forEach((k) => delete form[k])
  if (row) Object.assign(form, { ...row, children: undefined })
  else Object.assign(form, { menuType: 'MENU', parentId: parentId || 0, visible: 1, sortNo: 0, status: 1 })
  visible.value = true
}

async function save() {
  if (!form.menuName) return ElMessage.warning('请填写名称')
  await http.post('/system/menus', form)
  ElMessage.success('已保存')
  visible.value = false
  load()
}

async function remove(row: any) {
  await ElMessageBox.confirm(`删除菜单「${row.menuName}」？`)
  await http.delete(`/system/menus/${row.id}`)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.muted { color: #9aa8a2; font-size: 12px; }
</style>
