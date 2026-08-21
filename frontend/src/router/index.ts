import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '工作台' } },
        { path: 'todo', name: 'todo', component: () => import('@/views/task/TodoView.vue'), meta: { title: '我的待办' } },
        { path: 'done', name: 'done', component: () => import('@/views/task/DoneView.vue'), meta: { title: '我的已办' } },
        { path: 'started', name: 'started', component: () => import('@/views/task/StartedView.vue'), meta: { title: '我发起的' } },
        { path: 'cc', name: 'cc', component: () => import('@/views/task/CcView.vue'), meta: { title: '抄送我的' } },
        { path: 'start', name: 'start', component: () => import('@/views/task/StartProcessView.vue'), meta: { title: '发起审批' } },
        { path: 'task/:taskId', name: 'taskDetail', component: () => import('@/views/task/TaskDetailView.vue'), meta: { title: '任务详情' } },
        { path: 'instance/:processInstanceId', name: 'instanceDetail', component: () => import('@/views/task/InstanceDetailView.vue'), meta: { title: '审批详情' } },
        { path: 'process', name: 'process', component: () => import('@/views/process/ProcessListView.vue'), meta: { title: '流程管理' } },
        { path: 'process/design/:id?', name: 'processDesign', component: () => import('@/views/process/ProcessDesignerView.vue'), meta: { title: '流程设计器' } },
        { path: 'forms', name: 'forms', component: () => import('@/views/process/FormListView.vue'), meta: { title: '表单管理' } },
        { path: 'forms/design/:id?', name: 'formDesign', component: () => import('@/views/process/FormDesignerView.vue'), meta: { title: '表单设计器' } },
        { path: 'users', name: 'users', component: () => import('@/views/system/UserView.vue'), meta: { title: '用户管理' } },
        { path: 'depts', name: 'depts', component: () => import('@/views/system/DeptView.vue'), meta: { title: '部门管理' } },
        { path: 'roles', name: 'roles', component: () => import('@/views/system/RoleView.vue'), meta: { title: '角色管理' } },
        { path: 'menus', name: 'menus', component: () => import('@/views/system/MenuView.vue'), meta: { title: '菜单管理' } },
        { path: 'ticket-types', name: 'ticketTypes', component: () => import('@/views/ticket/TicketTypeView.vue'), meta: { title: '工单类型' } },
        { path: 'ticket-types/:id/form', name: 'ticketFormDesign', component: () => import('@/views/ticket/TicketFormDesignerView.vue'), meta: { title: '表单设计' } },
        { path: 'ticket-types/:id/list', name: 'ticketListDesign', component: () => import('@/views/ticket/TicketListDesignerView.vue'), meta: { title: '列表配置' } },
        { path: 'ticket-types/:id/detail', name: 'ticketDetailDesign', component: () => import('@/views/ticket/TicketDetailDesignerView.vue'), meta: { title: '详情配置' } },
        { path: 'tickets', name: 'tickets', component: () => import('@/views/ticket/TicketListView.vue'), meta: { title: '工单列表' } },
        { path: 'tickets/:typeCode/:id', name: 'ticketDetail', component: () => import('@/views/ticket/TicketDetailView.vue'), meta: { title: '工单详情' } },
        { path: 'tickets/:typeCode', name: 'ticketTypedList', component: () => import('@/views/ticket/TicketTypedListView.vue'), meta: { title: '工单列表' } },
        { path: 'messages', name: 'messages', component: () => import('@/views/NotifyView.vue'), meta: { title: '消息中心' } },
      ],
    },
  ],
})

router.beforeEach(async (to, _from, next) => {
  const token = localStorage.getItem('mw_token')
  if (!to.meta.public && !token) {
    next('/login')
    return
  }
  if (to.path === '/login' && token) {
    next('/')
    return
  }
  if (to.meta.public) {
    next()
    return
  }
  const store = useUserStore()
  if (token && !store.profile?.menus) {
    try {
      await store.fetchMe()
    } catch {
      store.logout()
      next('/login')
      return
    }
  }
  if (store.profile && !store.canAccess(to.path)) {
    next('/dashboard')
    return
  }
  next()
})

export default router
