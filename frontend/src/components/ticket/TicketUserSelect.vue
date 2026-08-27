<template>
  <el-select
    :model-value="modelValue"
    :multiple="multiple"
    filterable
    clearable
    :disabled="disabled"
    :placeholder="placeholder || '请选择人员'"
    style="width: 100%"
    @update:model-value="onUpdate"
  >
    <el-option v-for="u in options" :key="u.id" :label="u.label" :value="u.id" />
  </el-select>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import http from '@/utils/http'
import { loadUserNames, toIds, userName } from '@/utils/userNames'

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

const selectedIds = computed(() => toIds(props.modelValue))

/**
 * 精简列表只给前 50 个人，已选的人可能不在里面。
 * 缺了选项 el-select 会把 id 原样显示出来，所以按 id 补一条。
 */
const options = computed(() => {
  const list = users.value.map((u: any) => ({ id: String(u.id), label: u.realName || u.username }))
  const have = new Set(list.map((o) => o.id))
  for (const id of selectedIds.value) {
    if (!have.has(id)) list.unshift({ id, label: userName(id) })
  }
  return list
})

function onUpdate(v: any) {
  emit('update:modelValue', v)
  emit('change', v)
}

watch(selectedIds, (ids) => loadUserNames(ids), { immediate: true })

onMounted(async () => {
  const res: any = await http.get('/system/users/simple')
  users.value = res.data || []
})
</script>
