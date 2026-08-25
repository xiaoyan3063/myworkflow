<template>
  <div class="ticket-files">
    <el-upload
      :disabled="disabled"
      :show-file-list="false"
      :http-request="doUpload"
      multiple
    >
      <el-button size="small" :disabled="disabled" :loading="uploading">上传附件</el-button>
    </el-upload>
    <ul v-if="files.length" class="list">
      <li v-for="f in files" :key="f.id">
        <button type="button" class="link" @click="download(f.id)">{{ f.fileName }}</button>
        <span class="size">{{ fileSizeText(f.fileSize) }}</span>
        <el-button v-if="!disabled" link type="danger" @click="remove(f.id)">删除</el-button>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { UploadRequestOptions } from 'element-plus'
import { ElMessage } from 'element-plus'
import http from '@/utils/http'
import { downloadFile, fetchFileInfos, fileSizeText, normalizeFileIds } from './ticketFiles'

const props = defineProps<{
  modelValue?: Array<string | number> | string | number | null
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: string[]): void
  (e: 'change', v: string[]): void
}>()

const files = ref<any[]>([])
const uploading = ref(false)
/** 刚上传、后端还没绑定到工单的记录，先在本地留一份用于显示 */
const known = new Map<string, any>()

const ids = computed(() => normalizeFileIds(props.modelValue))

function emitIds(next: string[]) {
  if (next.join(',') === ids.value.join(',')) return
  emit('update:modelValue', next)
  emit('change', next)
}

async function loadMeta(nextIds: string[]) {
  if (!nextIds.length) {
    files.value = []
    return
  }
  const rows = await fetchFileInfos(nextIds)
  const byId = new Map(rows.map((r: any) => [String(r.id), r]))
  files.value = nextIds.map((id) => byId.get(id) || known.get(id)).filter(Boolean)
}

watch(
  () => ids.value.join(','),
  () => { loadMeta(ids.value) },
  { immediate: true },
)

async function doUpload(opt: UploadRequestOptions) {
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', opt.file as File)
    const res: any = await http.post('/ticket/files', fd)
    const rec = res.data
    if (!rec?.id) {
      ElMessage.error('上传失败')
      return
    }
    const id = String(rec.id)
    known.set(id, rec)
    const next = [...ids.value, id]
    files.value = [...files.value, rec]
    emitIds(next)
  } finally {
    uploading.value = false
  }
}

function remove(id: string) {
  emitIds(ids.value.filter((x) => x !== String(id)))
  files.value = files.value.filter((f) => String(f.id) !== String(id))
}

function download(id: string) {
  const hit = files.value.find((f) => String(f.id) === String(id))
  downloadFile(String(id), hit?.fileName)
}
</script>

<style scoped>
.list { margin: 8px 0 0; padding: 0; list-style: none; }
.list li { display: flex; align-items: center; gap: 8px; font-size: 13px; line-height: 1.8; }
.link { border: 0; background: none; color: var(--el-color-primary); cursor: pointer; padding: 0; }
.size { color: #9aa7a2; }
</style>
