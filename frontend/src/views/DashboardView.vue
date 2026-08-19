<template>
  <div>
    <h1 class="page-title">工作台</h1>
    <p class="page-sub">今日审批态势一览，快速进入待办与设计。</p>
    <div class="hero page-card">
      <div>
        <div class="hello">你好，{{ userStore.profile?.realName || '同事' }}</div>
        <p>把复杂审批沉淀成可复用的流程资产，业务系统只负责工单，审批交给 MyWorkflow。</p>
        <div class="cta">
          <el-button type="primary" @click="$router.push('/todo')">处理待办</el-button>
          <el-button @click="$router.push('/start')">发起审批</el-button>
          <el-button @click="$router.push('/process/design')">打开设计器</el-button>
        </div>
      </div>
      <div class="stats">
        <div class="stat"><b>{{ stats.todo }}</b><span>待办</span></div>
        <div class="stat"><b>{{ stats.started }}</b><span>我发起</span></div>
        <div class="stat"><b>{{ stats.unread }}</b><span>未读消息</span></div>
      </div>
    </div>
    <div class="grid">
      <div class="page-card">
        <h3>最近待办</h3>
        <el-table :data="todos" size="small" empty-text="暂无待办">
          <el-table-column prop="title" label="标题" min-width="160" />
          <el-table-column prop="taskName" label="节点" width="120" />
          <el-table-column prop="starterName" label="发起人" width="100" />
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button link type="primary" @click="$router.push(`/task/${row.taskId}`)">办理</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="page-card tip">
        <h3>对接指引</h3>
        <ol>
          <li>在流程管理中设计并发布 BPMN</li>
          <li>CRM 通过开放接口携带 businessKey 发起</li>
          <li>审批人由用户 / 角色 / 部门解析</li>
          <li>完成后回调或主动查询实例状态</li>
        </ol>
        <code>X-App-Key: crm_demo_key</code>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import http from '@/utils/http'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const todos = ref<any[]>([])
const stats = reactive({ todo: 0, started: 0, unread: 0 })

onMounted(async () => {
  const [todoRes, startedRes, unreadRes]: any[] = await Promise.all([
    http.get('/runtime/todo', { params: { page: 1, size: 5 } }),
    http.get('/runtime/started', { params: { page: 1, size: 1 } }),
    http.get('/notify/unread-count'),
  ])
  todos.value = todoRes.data?.records || []
  stats.todo = todoRes.data?.total || 0
  stats.started = startedRes.data?.total || 0
  stats.unread = unreadRes.data?.count || 0
})
</script>

<style scoped lang="scss">
.hero {
  display: grid;
  grid-template-columns: 1.4fr 0.8fr;
  gap: 24px;
  margin-bottom: 20px;
  background:
    linear-gradient(135deg, rgba(11, 61, 46, 0.96), rgba(31, 107, 79, 0.9)),
    radial-gradient(circle at 80% 20%, rgba(200, 245, 96, 0.35), transparent 40%);
  color: #f2f7f0;
}
.hello {
  font-family: 'Instrument Serif', Georgia, serif;
  font-size: 36px;
  margin-bottom: 10px;
}
.hero p { opacity: 0.82; line-height: 1.7; max-width: 560px; }
.cta { margin-top: 20px; display: flex; gap: 10px; flex-wrap: wrap; }
.stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; align-content: center; }
.stat {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 16px;
  padding: 18px 12px;
  text-align: center;
}
.stat b { display: block; font-size: 30px; color: #c8f560; }
.stat span { font-size: 13px; opacity: 0.75; }
.grid { display: grid; grid-template-columns: 1.4fr 0.8fr; gap: 18px; }
.page-card h3 { margin: 0 0 14px; color: #0b3d2e; }
.tip ol { padding-left: 18px; line-height: 1.9; color: rgba(11, 31, 26, 0.72); }
.tip code {
  display: inline-block;
  margin-top: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  background: #e8f2ec;
  color: #0b3d2e;
}
@media (max-width: 960px) {
  .hero, .grid { grid-template-columns: 1fr; }
}
</style>
