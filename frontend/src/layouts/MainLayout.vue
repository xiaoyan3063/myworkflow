<template>
  <div class="layout">
    <aside class="sider">
      <div class="logo">
        <div class="mark">MW</div>
        <div>
          <strong>MyWorkflow</strong>
          <small>Approval OS</small>
        </div>
      </div>
      <el-menu :default-active="activeMenu" router class="menu">
        <SideMenuItems :items="userStore.profile?.menus || []" />
      </el-menu>
    </aside>
    <section class="main">
      <header class="topbar">
        <div>
          <div class="crumb">{{ route.meta.title || '工作台' }}</div>
          <div class="time">{{ now }}</div>
        </div>
        <div class="actions">
          <el-badge :value="unread" :hidden="!unread" class="badge">
            <el-button circle text @click="$router.push('/messages')"><el-icon><Bell /></el-icon></el-button>
          </el-badge>
          <el-dropdown>
            <div class="user">
              <el-avatar :size="34">{{ (userStore.profile?.realName || 'U').slice(0, 1) }}</el-avatar>
              <span>{{ userStore.profile?.realName || userStore.profile?.username }}</span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import http from '@/utils/http'
import SideMenuItems from './SideMenuItems.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const unread = ref(0)
const now = ref(new Date().toLocaleString())

const activeMenu = computed(() => {
  const p = route.path
  if (p.startsWith('/tickets/')) {
    const segs = p.split('/').filter(Boolean)
    if (segs.length >= 2) return `/tickets/${segs[1]}`
  }
  if (p.startsWith('/ticket-types')) return '/ticket-types'
  if (p.startsWith('/process')) return '/process'
  if (p.startsWith('/forms')) return '/forms'
  return p
})

onMounted(async () => {
  setInterval(() => { now.value = new Date().toLocaleString() }, 1000)
  if (!userStore.profile) {
    try { await userStore.fetchMe() } catch { /* ignore */ }
  }
  try {
    const res: any = await http.get('/notify/unread-count')
    unread.value = res.data?.count || 0
  } catch { /* ignore */ }
})

function logout() {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped lang="scss">
.layout {
  height: 100vh;
  display: grid;
  grid-template-columns: 248px 1fr;
  overflow: hidden;
}
.sider {
  padding: 22px 14px;
  background: linear-gradient(180deg, #0b3d2e 0%, #072820 100%);
  color: #e8f2ec;
  box-shadow: 12px 0 40px rgba(11, 61, 46, 0.18);
  overflow-y: auto;
}
.logo {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 8px 12px 24px;
}
.mark {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: #c8f560;
  color: #0b3d2e;
  display: grid;
  place-items: center;
  font-weight: 700;
}
.logo strong { display: block; font-size: 16px; }
.logo small { opacity: 0.65; font-size: 12px; }
.menu {
  border-right: none;
  background: transparent;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: rgba(242, 247, 240, 0.82);
  --el-menu-hover-bg-color: rgba(200, 245, 96, 0.12);
  --el-menu-active-color: #c8f560;
}
.main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.topbar {
  flex: none;
  height: 72px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 28px;
  border-bottom: 1px solid rgba(11, 61, 46, 0.08);
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(10px);
}
.crumb {
  font-family: 'Instrument Serif', Georgia, serif;
  font-size: 26px;
  color: #0b3d2e;
}
.time { font-size: 12px; color: rgba(11, 31, 26, 0.45); margin-top: 2px; }
.actions { display: flex; align-items: center; gap: 14px; }
.user {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}
.content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 24px 28px 32px;
}
/* 单卡片页面撑满可视区，避免内容区显得过矮 */
.content > :deep(.page-card) {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}
@media (max-width: 960px) {
  .layout {
    height: auto;
    grid-template-columns: 1fr;
    overflow: visible;
  }
  .sider { display: none; }
  .main { overflow: visible; }
  .content { overflow: visible; }
}
</style>
