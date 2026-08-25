<template>
  <span v-if="!ids.length">-</span>
  <el-popover v-else placement="bottom-start" :width="320" trigger="click" @show="loadMeta">
    <template #reference>
      <el-link type="primary" :underline="false">{{ ids.length }} 个附件</el-link>
    </template>
    <ul v-if="files.length" class="files">
      <li v-for="f in files" :key="f.id">
        <button type="button" class="link" @click="open(f)">{{ f.fileName }}</button>
        <span class="size">{{ fileSizeText(f.fileSize) }}</span>
      </li>
    </ul>
    <p v-else-if="loading" class="tip">加载中…</p>
    <p v-else class="tip">附件已被删除，或你无权查看</p>
  </el-popover>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { downloadFile, fetchFileInfos, fileSizeText, normalizeFileIds } from './ticketFiles'

const props = defineProps<{ value?: any }>()

const files = ref<any[]>([])
const loading = ref(false)
const loaded = ref(false)

const ids = computed(() => normalizeFileIds(props.value))

async function loadMeta() {
  if (loaded.value) return
  loading.value = true
  try {
    files.value = await fetchFileInfos(ids.value)
    loaded.value = true
  } finally {
    loading.value = false
  }
}

function open(f: any) {
  downloadFile(String(f.id), f.fileName)
}
</script>

<style scoped>
.files { margin: 0; padding: 0; list-style: none; }
.files li { display: flex; align-items: center; gap: 8px; line-height: 1.9; font-size: 13px; }
.link { border: 0; background: none; padding: 0; color: var(--el-color-primary); cursor: pointer; text-align: left; }
.size { color: #9aa7a2; }
.tip { margin: 0; color: #9aa7a2; font-size: 13px; }
</style>
