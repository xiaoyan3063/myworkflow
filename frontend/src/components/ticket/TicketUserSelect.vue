<template>
  <el-select
    :model-value="modelValue"
    :multiple="multiple"
    filterable
    clearable
    :disabled="disabled"
    :placeholder="placeholder || (multiple ? '请选择人员' : '请选择人员')"
    style="width: 100%"
    @update:model-value="onUpdate"
  >
    <el-option v-for="u in users" :key="u.id" :label="u.realName || u.username" :value="String(u.id)" />
  </el-select>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '@/utils/http'

const props = defineProps<{
  modelValue?: string | string[] | number | null
  disabled?: boolean
  multiple?: boolean
  placeholder?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: any): void
  (e: 'change', v: any): void
}>()

const users = ref<any[]>([])

function onUpdate(v: any) {
  emit('update:modelValue', v)
  emit('change', v)
}

onMounted(async () => {
  const res: any = await http.get('/system/users/simple')
  users.value = res.data || []
})
</script>
