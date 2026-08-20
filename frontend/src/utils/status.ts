/** 流程实例状态的统一中文文案，列表和详情共用，避免各页面各写一套 */
export const INSTANCE_STATUS: Record<string, { text: string; tone: string }> = {
  RUNNING: { text: '审批中', tone: 'warning' },
  COMPLETED: { text: '已通过', tone: 'success' },
  REJECTED: { text: '已驳回', tone: 'danger' },
  CANCELLED: { text: '已撤销', tone: 'info' },
  TERMINATED: { text: '已终止', tone: 'info' },
}

export function statusText(status?: string) {
  if (!status) return '-'
  return INSTANCE_STATUS[status]?.text || status
}

export function statusTone(status?: string) {
  return INSTANCE_STATUS[status || '']?.tone || 'info'
}
