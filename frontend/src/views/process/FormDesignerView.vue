<template>
  <div class="page-card">
    <h1 class="page-title">表单设计器</h1>
    <p class="page-sub">可视化配置字段，保存为 JSON Schema</p>
    <el-form inline>
      <el-form-item label="名称"><el-input v-model="meta.formName" /></el-form-item>
      <el-form-item label="标识"><el-input v-model="meta.formKey" :disabled="!!meta.id" /></el-form-item>
      <el-button type="primary" @click="save">保存</el-button>
      <el-button @click="addField">添加字段</el-button>
    </el-form>
    <el-table :data="fields" style="margin-top: 12px">
      <el-table-column label="标题" min-width="140">
        <template #default="{ row }"><el-input v-model="row.title" /></template>
      </el-table-column>
      <el-table-column label="字段名" width="140">
        <template #default="{ row }"><el-input v-model="row.field" /></template>
      </el-table-column>
      <el-table-column label="类型" width="140">
        <template #default="{ row }">
          <el-select v-model="row.type">
            <el-option label="单行文本" value="input" />
            <el-option label="多行文本" value="textarea" />
            <el-option label="数字" value="number" />
            <el-option label="下拉" value="select" />
            <el-option label="人员单选" value="user" />
            <el-option label="人员多选" value="users" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="选项(JSON)" min-width="220">
        <template #default="{ row }">
          <el-input v-if="row.type === 'select'" v-model="row.optionsText" placeholder='[{"label":"年假","value":"annual"}]' />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ $index }">
          <el-button link type="danger" @click="fields.splice($index, 1)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/utils/http'

const route = useRoute()
const router = useRouter()
const meta = reactive<any>({ id: null, formName: '', formKey: '', status: 1 })
const fields = ref<any[]>([])

function addField() {
  fields.value.push({ title: '新字段', field: `field_${fields.value.length + 1}`, type: 'input', optionsText: '' })
}

onMounted(async () => {
  if (route.params.id) {
    const res: any = await http.get(`/process/forms/${route.params.id}`)
    Object.assign(meta, res.data)
    const schema = JSON.parse(res.data.formSchema || '[]')
    fields.value = schema.map((f: any) => ({
      ...f,
      optionsText: f.options ? JSON.stringify(f.options) : '',
    }))
  } else {
    addField()
  }
})

async function save() {
  if (!meta.formName || !meta.formKey) return ElMessage.warning('请填写名称和标识')
  const schema = fields.value.map((f) => {
    const item: any = { type: f.type, field: f.field, title: f.title }
    if (f.type === 'select' && f.optionsText) {
      try { item.options = JSON.parse(f.optionsText) } catch { item.options = [] }
    }
    return item
  })
  const res: any = await http.post('/process/forms', {
    id: meta.id,
    formName: meta.formName,
    formKey: meta.formKey,
    formSchema: JSON.stringify(schema),
    status: 1,
  })
  meta.id = res.data.id
  ElMessage.success('保存成功')
  if (!route.params.id) router.replace(`/forms/design/${meta.id}`)
}
</script>
