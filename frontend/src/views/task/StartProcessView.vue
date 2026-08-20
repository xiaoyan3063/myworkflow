<template>
  <div class="page-card">
    <h1 class="page-title">发起审批</h1>
    <p class="page-sub">选择已发布流程并填写业务信息</p>
    <el-form label-width="100px" style="max-width: 720px">
      <el-form-item label="选择流程">
        <el-select v-model="form.processDefId" placeholder="请选择" style="width: 100%" @change="onProcessChange">
          <el-option v-for="p in processes" :key="p.id" :label="p.processName" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="标题">
        <el-input v-model="form.title" placeholder="审批标题" />
      </el-form-item>
      <el-form-item label="业务单号">
        <el-input v-model="form.businessKey" placeholder="对接 CRM 工单号" />
      </el-form-item>
      <el-form-item label="业务类型">
        <el-input v-model="form.businessType" placeholder="如 leave / contract" />
      </el-form-item>
      <template v-if="schema.length">
        <el-divider>表单字段</el-divider>
        <el-form-item v-for="f in schema" :key="f.field" :label="f.title">
          <el-input v-if="f.type === 'input' || !f.type" v-model="form.formData[f.field]" />
          <el-input v-else-if="f.type === 'textarea'" type="textarea" v-model="form.formData[f.field]" />
          <el-input-number v-else-if="f.type === 'number'" v-model="form.formData[f.field]" />
          <el-select v-else-if="f.type === 'select'" v-model="form.formData[f.field]" style="width: 100%">
            <el-option v-for="o in f.options || []" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-select
            v-else-if="f.type === 'user' || f.type === 'users'"
            v-model="form.formData[f.field]"
            :multiple="f.type === 'users'"
            filterable
            style="width: 100%"
            placeholder="请选择人员"
          >
            <el-option
              v-for="u in users"
              :key="u.id"
              :label="`${u.realName || u.username}（${u.username}）`"
              :value="String(u.id)"
            />
          </el-select>
        </el-form-item>
      </template>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="submit">提交发起</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import http from '@/utils/http'

const router = useRouter()
const processes = ref<any[]>([])
const schema = ref<any[]>([])
const users = ref<any[]>([])
const loading = ref(false)
const form = reactive<any>({
  processDefId: undefined,
  title: '',
  businessKey: '',
  businessType: '',
  formData: {},
})

onMounted(async () => {
  const [processRes, userRes]: any[] = await Promise.all([
    http.get('/process/defs/published'),
    http.get('/system/users/simple'),
  ])
  processes.value = processRes.data || []
  users.value = userRes.data || []
})

async function onProcessChange(id: number) {
  const p = processes.value.find((x) => x.id === id)
  if (!p?.formId) { schema.value = []; return }
  const res: any = await http.get(`/process/forms/${p.formId}`)
  try {
    schema.value = JSON.parse(res.data.formSchema || '[]')
  } catch {
    schema.value = []
  }
  form.formData = {}
  schema.value.forEach((f) => {
    form.formData[f.field] = f.value ?? (f.type === 'users' ? [] : '')
  })
}

async function submit() {
  if (!form.processDefId) return ElMessage.warning('请选择流程')
  loading.value = true
  try {
    await http.post('/runtime/start', form)
    ElMessage.success('发起成功')
    router.push('/started')
  } finally {
    loading.value = false
  }
}
</script>
