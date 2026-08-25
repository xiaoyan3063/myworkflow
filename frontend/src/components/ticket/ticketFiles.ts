import { ElMessage } from 'element-plus'
import http from '@/utils/http'

/** 雪花 ID 有 19 位，转成 Number 会丢精度，一律按字符串处理 */
export function normalizeFileIds(v: any): string[] {
  if (v === undefined || v === null || v === '') return []
  const arr = Array.isArray(v) ? v : [v]
  return arr.map((x) => String(x).trim()).filter((s) => /^\d+$/.test(s))
}

export function looksLikeFileIds(v: any): boolean {
  const ids = normalizeFileIds(v)
  return ids.length > 0 && ids.every((s) => s.length >= 15)
}

export async function fetchFileInfos(ids: string[]): Promise<any[]> {
  if (!ids.length) return []
  const res: any = await http.get('/ticket/files', { params: { ids: ids.join(',') } })
  const byId = new Map((res.data || []).map((r: any) => [String(r.id), r]))
  return ids.map((id) => byId.get(id)).filter(Boolean) as any[]
}

export function fileSizeText(n?: number) {
  if (!n) return ''
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(1) + ' MB'
}

export async function downloadFile(id: string, fileName?: string) {
  const token = localStorage.getItem('mw_token')
  const resp = await fetch(`/api/ticket/files/${id}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!resp.ok) {
    ElMessage.error(resp.status === 403 ? '无权下载该附件' : '下载失败')
    return
  }
  const blob = await resp.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName || 'file'
  a.click()
  URL.revokeObjectURL(url)
}
