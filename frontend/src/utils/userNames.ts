import { ref } from 'vue'
import http from '@/utils/http'

/**
 * 表单人员字段存的是用户 id，列表和只读视图要显示人名。
 * 缓存放在模块级：一次会话里同一个人只查一次，多个页面共用。
 */
const names = ref<Record<string, string>>({})
const inflight = new Set<string>()

export function userName(id: any): string {
  const key = String(id ?? '')
  return names.value[key] || key
}

export function userNamesOf(v: any): string {
  if (v === undefined || v === null || v === '') return ''
  return toIds(v).map(userName).join('、')
}

export function toIds(v: any): string[] {
  const list = Array.isArray(v) ? v : [v]
  return list.map((x) => String(x ?? '').trim()).filter(Boolean)
}

export async function loadUserNames(ids: any[]) {
  const want = [...new Set(ids.flatMap(toIds))].filter((id) => !names.value[id] && !inflight.has(id))
  if (!want.length) return
  want.forEach((id) => inflight.add(id))
  try {
    const res: any = await http.get('/system/users/by-ids', { params: { ids: want.join(',') } })
    const next = { ...names.value }
    for (const u of res.data || []) {
      next[String(u.id)] = u.realName || u.username
    }
    names.value = next
  } catch {
    // 查不到就保持原值显示，不影响列表渲染
  } finally {
    want.forEach((id) => inflight.delete(id))
  }
}
