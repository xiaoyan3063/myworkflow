import { defineStore } from 'pinia'
import { ref } from 'vue'
import http from '@/utils/http'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('mw_token') || '')
  const profile = ref<any>(null)

  async function login(username: string, password: string) {
    const res: any = await http.post('/auth/login', { username, password },
      { headers: { 'X-Client-Type': 'WEB' } })
    token.value = res.data.token
    localStorage.setItem('mw_token', res.data.token)
    profile.value = res.data
    return res.data
  }

  async function fetchMe() {
    const res: any = await http.get('/auth/me')
    profile.value = { ...(profile.value || {}), ...res.data }
    return res.data
  }

  function hasPerm(perm: string) {
    if (!perm) return true
    if (profile.value?.admin) return true
    const perms: string[] = profile.value?.perms || []
    return perms.indexOf(perm) >= 0
  }

  function canAccess(path: string) {
    if (profile.value?.admin) return true
    // 审批中心个人视图是所有登录用户均可访问的弱菜单，不代表拥有工单数据权限。
    const always = ['/dashboard', '/messages', '/todo', '/done', '/started', '/cc']
    if (always.some((p) => path === p || path.startsWith(p + '/'))) return true
    if (path.startsWith('/task/') || path.startsWith('/instance/')) return true
    const paths: string[] = []
    walk(profile.value?.menus || [], paths)
    return paths.some((mp) => mp && (path === mp || path.startsWith(mp + '/')))
  }

  function walk(nodes: any[], out: string[]) {
    for (const n of nodes || []) {
      if (n.path) out.push(n.path)
      if (n.children && n.children.length) walk(n.children, out)
    }
  }

  function logout() {
    token.value = ''
    profile.value = null
    localStorage.removeItem('mw_token')
  }

  return { token, profile, login, fetchMe, logout, hasPerm, canAccess }
})
