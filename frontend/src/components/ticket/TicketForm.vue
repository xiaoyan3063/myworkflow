<template>
  <form-create
    v-if="rule.length"
    v-model="inner"
    v-model:api="fapi"
    :rule="rule"
    :option="silentFormOption"
    :disabled="disabled"
  />
  <el-empty v-else description="尚未设计表单，请先在工单类型中打开表单设计器" />
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { rulesFromSchema, silentFormOption } from './fcTicketRules'

const props = defineProps<{
  schema?: any
  modelValue?: Record<string, any>
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: Record<string, any>): void
}>()

const fapi = ref<any>()
const inner = ref<Record<string, any>>({ ...(props.modelValue || {}) })

const rule = computed(() => rulesFromSchema(props.schema))

watch(
  () => props.modelValue,
  (v) => {
    inner.value = { ...(v || {}) }
  },
  { deep: true },
)

watch(
  inner,
  (v) => {
    emit('update:modelValue', { ...v })
  },
  { deep: true },
)
</script>
