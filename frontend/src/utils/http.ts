import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('mw_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (res) => {
    const data = res.data
    if (data && typeof data.code === 'number' && data.code !== 0) {
      ElMessage.error(data.message || '请求失败')
      if (data.code === 401) {
        localStorage.removeItem('mw_token')
        router.push('/login')
      }
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return data
  },
  (err) => {
    const msg = err?.response?.data?.message || err.message || '网络异常'
    if (err?.response?.status === 401) {
      localStorage.removeItem('mw_token')
      router.push('/login')
    }
    ElMessage.error(msg)
    return Promise.reject(err)
  }
)

export default http
