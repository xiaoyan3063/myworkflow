<template>
  <template v-for="m in items" :key="m.id">
    <el-sub-menu v-if="m.menuType === 'DIR'" :index="'dir-' + m.id">
      <template #title>
        <el-icon v-if="m.icon"><component :is="m.icon" /></el-icon>
        <span>{{ m.menuName }}</span>
      </template>
      <SideMenuItems :items="m.children || []" />
    </el-sub-menu>
    <el-menu-item v-else-if="m.path" :index="m.path">
      <el-icon v-if="m.icon"><component :is="m.icon" /></el-icon>
      <span>{{ m.menuName }}</span>
    </el-menu-item>
  </template>
</template>
<script setup lang="ts">
defineOptions({ name: 'SideMenuItems' })
defineProps<{ items: any[] }>()
</script>
