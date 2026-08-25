<template>
  <form-create
    v-if="rule.length"
    v-model="inner"
    v-model:api="fapi"
    :rule="rule"
    :option="formOption"
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
  onlyFields?: string[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: Record<string, any>): void
}>()

const fapi = ref<any>()
const inner = ref<Record<string, any>>({ ...(props.modelValue || {}) })

const rule = computed(() => rulesFromSchema(props.schema, props.onlyFields))
const formOption = computed(() => ({
  ...silentFormOption,
  form: { ...(silentFormOption.form || {}), disabled: !!props.disabled },
}))

/** 父子各有一个深度 watch，值相同还回抛会把两边打成死循环 */
function same(a: any, b: any) {
  return JSON.stringify(a || {}) === JSON.stringify(b || {})
}

watch(
  () => props.modelValue,
  (v) => {
    if (same(v, inner.value)) return
    inner.value = { ...(v || {}) }
  },
  { deep: true },
)

watch(
  inner,
  (v) => {
    if (same(v, props.modelValue)) return
    emit('update:modelValue', { ...v })
  },
  { deep: true },
)
</script>
