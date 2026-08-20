<template>
  <div class="track">
    <div class="track-row">
      <span class="dot done" />
      <div class="track-main">
        <div class="track-title">发起人</div>
        <div class="actor">
          <span class="avatar">{{ initial(data.starterName) }}</span>
          <span class="actor-name">{{ data.starterName || '-' }}</span>
          <span class="actor-act">发起流程</span>
          <span class="actor-time">{{ data.startTime }}</span>
        </div>
      </div>
    </div>

    <div v-for="(n, i) in data.nodes || []" :key="i" class="track-row">
      <span class="dot" :class="nodeDot(n.status)" />
      <div class="track-main">
        <div class="track-title">
          {{ n.activityName }}
          <span v-if="n.durationText" class="cost">耗时: {{ n.durationText }}</span>
        </div>
        <div class="track-status" :class="nodeDot(n.status)">{{ n.statusText }}</div>
        <div v-for="(h, hi) in n.handlers || []" :key="hi" class="actor">
          <span class="avatar" :class="actorTone(h.action)">{{ initial(h.name) }}</span>
          <span class="actor-name">{{ h.name }}</span>
          <span class="actor-act" :class="actorTone(h.action)">{{ h.actionText }}</span>
          <span class="actor-time">{{ h.time }}</span>
          <div v-if="h.comment" class="actor-comment">{{ h.comment }}</div>
        </div>
      </div>
    </div>

    <div v-if="data.finished" class="track-row">
      <span class="dot end" />
      <div class="track-main">
        <div class="track-title muted">流程结束</div>
        <div v-if="data.endTime" class="actor-time end-time">{{ data.endTime }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ data: any }>()

function initial(name?: string) {
  return name ? name.trim().charAt(0) : '-'
}

function nodeDot(status: string) {
  if (status === 'REJECTED') return 'reject'
  if (status === 'PENDING') return 'pending'
  return 'done'
}

function actorTone(action: string) {
  if (action === 'REJECT') return 'reject'
  if (action === 'PENDING') return 'pending'
  if (action === 'CANCELLED') return 'muted'
  return 'done'
}
</script>

<style scoped>
.track {
  position: relative;
  padding-left: 4px;
}
/* 竖线画在行上而不是整体容器上，最后一行不再往下延伸 */
.track-row {
  position: relative;
  display: flex;
  gap: 12px;
  padding-bottom: 18px;
}
.track-row::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 14px;
  bottom: 0;
  width: 2px;
  background: #e4e9e7;
}
.track-row:last-child::before {
  display: none;
}
.dot {
  position: relative;
  z-index: 1;
  flex: none;
  width: 12px;
  height: 12px;
  margin-top: 4px;
  border-radius: 50%;
  background: #409eff;
}
.dot.pending {
  background: #409eff;
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.18);
}
.dot.reject {
  background: #f56c6c;
}
.dot.end {
  background: #c8cfcd;
}
.track-main {
  flex: 1;
  min-width: 0;
}
.track-title {
  font-weight: 600;
  color: #1f2d29;
  font-size: 14px;
}
.track-title.muted {
  color: #9aa7a2;
  font-weight: 500;
}
.cost {
  margin-left: 8px;
  font-weight: 400;
  font-size: 12px;
  color: #8a9a94;
}
.track-status {
  font-size: 12px;
  margin-top: 2px;
}
.track-status.done {
  color: #67c23a;
}
.track-status.reject {
  color: #f56c6c;
}
.track-status.pending {
  color: #409eff;
}
.actor {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  background: #f5f7f9;
  border-radius: 8px;
  padding: 8px 10px;
  margin-top: 8px;
}
.avatar {
  flex: none;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #409eff;
}
.avatar.reject {
  background: #f56c6c;
}
.avatar.muted {
  background: #c8cfcd;
}
.actor-name {
  color: #1f2d29;
  font-size: 13px;
}
.actor-act {
  font-size: 13px;
  color: #8a9a94;
}
.actor-act.done {
  color: #67c23a;
}
.actor-act.reject {
  color: #f56c6c;
}
.actor-act.pending {
  color: #8a9a94;
}
.actor-time {
  color: #9aa7a2;
  font-size: 12px;
}
.end-time {
  margin-top: 4px;
}
.actor-comment {
  flex-basis: 100%;
  color: #46544f;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
  padding-left: 32px;
}
</style>
