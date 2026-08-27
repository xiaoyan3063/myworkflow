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
  /** 审批节点字段权限；传入后，仅名单内字段可编辑，其余字段只读 */
  editableFields?: string[]
  /** editableFields 中本节点办理前必须填写的字段 */
  requiredFields?: string[]
  /** 归属后续审批节点、当前环节还不该露出的字段 */
  hiddenFields?: string[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: Record<string, any>): void
}>()

const fapi = ref<any>()
const inner = ref<Record<string, any>>({ ...(props.modelValue || {}) })

const rule = computed(() => applyFieldAccess(rulesFromSchema(props.schema, props.onlyFields)))
const formOption = computed(() => ({
  ...silentFormOption,
  form: { ...(silentFormOption.form || {}), disabled: !!props.disabled },
}))

function applyFieldAccess(rules: any[]): any[] {
  const editable = props.editableFields ? new Set(props.editableFields) : null
  const required = new Set(props.requiredFields || [])
  const hidden = new Set(props.hiddenFields || [])
  const walk = (items: any[]): any[] => {
    const out: any[] = []
    for (const item of items || []) {
      if (!item || typeof item !== 'object') {
        out.push(item)
        continue
      }
      if (item.field && hidden.has(item.field)) continue
      const next = { ...item, props: { ...(item.props || {}) } }
      if (item.field) {
        const canEdit = !props.disabled && (!editable || editable.has(item.field))
        next.props.disabled = !canEdit
        // 节点必填规则只约束该节点可编辑字段；只读字段不参与当前表单校验
        if (editable) next.$required = canEdit && required.has(item.field)
      }
      if (Array.isArray(item.children)) {
        next.children = walk(item.children)
        // 字段全被隐藏后，只剩标题/分割线的布局容器一并去掉，否则会留下空白的栅格、卡片
        if (!item.field && hasField(item.children) && !hasField(next.children)) continue
      }
      out.push(next)
    }
    return out
  }
  return walk(rules)
}

function hasField(items: any[]): boolean {
  return (items || []).some(
    (item: any) =>
      item && typeof item === 'object' && (!!item.field || hasField(item.children)),
  )
}

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
