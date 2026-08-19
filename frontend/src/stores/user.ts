import { defineStore } from 'pinia'
import { ref } from 'vue'
import http from '@/utils/http'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('mw_token') || '')
  const profile = ref<any>(null)

  async function login(username: string, password: string) {
    const res: any = await http.post('/auth/login', { username, password })
    token.value = res.data.token
    localStorage.setItem('mw_token', res.data.token)
    profile.value = res.data
    return res.data
  }

  async function fetchMe() {
    const res: any = await http.get('/auth/me')
    profile.value = res.data
    return res.data
  }

  function logout() {
    token.value = ''
    profile.value = null
    localStorage.removeItem('mw_token')
  }

  return { token, profile, login, fetchMe, logout }
})
