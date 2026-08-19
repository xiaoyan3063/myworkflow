<template>
  <div class="login-page">
    <div class="login-hero">
      <div class="brand-mark">MW</div>
      <h1>MyWorkflow</h1>
      <p>独立审批流平台 · 连接业务 · 驱动决策</p>
      <div class="orbit">
        <span></span><span></span><span></span>
      </div>
    </div>
    <div class="login-panel">
      <h2>欢迎回来</h2>
      <p class="hint">使用组织账号登录审批工作台</p>
      <el-form :model="form" @keyup.enter="onLogin">
        <el-form-item>
          <el-input v-model="form.username" size="large" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" size="large" type="password" show-password placeholder="密码" prefix-icon="Lock" />
        </el-form-item>
        <el-button type="primary" size="large" class="submit" :loading="loading" @click="onLogin">进入工作台</el-button>
      </el-form>
      <div class="demo">演示账号：admin / admin123 · manager / admin123</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'admin123' })

async function onLogin() {
  loading.value = true
  try {
    await userStore.login(form.username, form.password)
    ElMessage.success('登录成功')
    router.push('/')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
}
.login-hero {
  position: relative;
  overflow: hidden;
  padding: 72px;
  color: #f2f7f0;
  background:
    linear-gradient(145deg, rgba(11, 61, 46, 0.92), rgba(7, 40, 32, 0.88)),
    url('data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" viewBox="0 0 160 160"><path d="M0 80h160M80 0v160" stroke="%23c8f560" stroke-opacity="0.08" stroke-width="1"/></svg>');
  animation: heroIn 0.8s ease;
}
.brand-mark {
  width: 64px;
  height: 64px;
  border-radius: 18px;
  display: grid;
  place-items: center;
  font-weight: 700;
  letter-spacing: 0.08em;
  background: #c8f560;
  color: #0b3d2e;
  margin-bottom: 28px;
}
.login-hero h1 {
  font-family: 'Instrument Serif', Georgia, serif;
  font-size: 72px;
  font-weight: 400;
  margin: 0 0 12px;
}
.login-hero p {
  font-size: 18px;
  opacity: 0.82;
  max-width: 420px;
}
.orbit {
  position: absolute;
  right: -40px;
  bottom: -40px;
  width: 360px;
  height: 360px;
  border: 1px solid rgba(200, 245, 96, 0.25);
  border-radius: 50%;
  animation: spin 18s linear infinite;
}
.orbit span {
  position: absolute;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #c8f560;
}
.orbit span:nth-child(1) { top: 20px; left: 50%; }
.orbit span:nth-child(2) { bottom: 48px; left: 36px; background: #f2f7f0; }
.orbit span:nth-child(3) { top: 46%; right: 18px; }
.login-panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 64px 72px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(16px);
  animation: panelIn 0.7s ease 0.1s both;
}
.login-panel h2 {
  font-family: 'Instrument Serif', Georgia, serif;
  font-size: 40px;
  margin: 0 0 8px;
  color: #0b3d2e;
}
.hint { color: rgba(11, 31, 26, 0.55); margin-bottom: 28px; }
.submit { width: 100%; margin-top: 8px; height: 48px; font-weight: 600; }
.demo { margin-top: 24px; font-size: 13px; color: rgba(11, 31, 26, 0.45); }
@keyframes heroIn {
  from { opacity: 0; transform: translateX(-16px); }
  to { opacity: 1; transform: none; }
}
@keyframes panelIn {
  from { opacity: 0; transform: translateY(18px); }
  to { opacity: 1; transform: none; }
}
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 900px) {
  .login-page { grid-template-columns: 1fr; }
  .login-hero { min-height: 280px; padding: 40px; }
  .login-hero h1 { font-size: 48px; }
  .login-panel { padding: 40px 28px; }
}
</style>
