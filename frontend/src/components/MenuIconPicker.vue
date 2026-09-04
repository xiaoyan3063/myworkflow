<template>
  <div class="picker">
    <el-input v-model="keyword" clearable placeholder="搜索图标" style="margin-bottom: 8px" />
    <div class="grid">
      <button
        v-for="item in filtered"
        :key="item.name"
        type="button"
        class="cell"
        :class="{ active: modelValue === item.name }"
        :title="item.label"
        @click="emit('update:modelValue', item.name)"
      >
        <el-icon :size="18"><component :is="item.name" /></el-icon>
        <span>{{ item.name }}</span>
      </button>
    </div>
    <el-button v-if="modelValue" link type="info" @click="emit('update:modelValue', '')">清除</el-button>
  </div>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { MENU_ICONS } from '@/utils/menuIcons'

const props = defineProps<{ modelValue?: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>()
const keyword = ref('')

const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return MENU_ICONS
  return MENU_ICONS.filter(item =>
    item.name.toLowerCase().includes(q) || item.label.includes(keyword.value.trim()),
  )
})
</script>
<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
  max-height: 280px;
  overflow: auto;
}
.cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px 4px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  color: var(--el-text-color-regular);
}
.cell span { font-size: 11px; line-height: 1.2; word-break: break-all; }
.cell:hover, .cell.active { border-color: var(--el-color-primary); color: var(--el-color-primary); }
</style>
