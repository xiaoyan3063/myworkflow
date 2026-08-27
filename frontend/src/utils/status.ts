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

export const TICKET_STATUS: Record<string, { text: string; tone: string }> = {
  DRAFT: { text: '草稿', tone: 'info' },
  IN_APPROVAL: { text: '审批中', tone: 'warning' },
  APPROVED: { text: '已通过', tone: 'success' },
  REJECTED: { text: '已驳回', tone: 'danger' },
  CANCELLED: { text: '已撤销', tone: 'info' },
}

export function ticketStatusText(status?: string) {
  if (!status) return '-'
  return TICKET_STATUS[status]?.text || status
}

export function ticketStatusTone(status?: string) {
  return TICKET_STATUS[status || '']?.tone || 'info'
}

/** 站内消息 msgType，库里存英文码，列表显示中文 */
export const MSG_TYPE: Record<string, { text: string; tone: string }> = {
  TODO: { text: '待办', tone: 'warning' },
  CC: { text: '抄送', tone: 'info' },
  REJECT: { text: '驳回', tone: 'danger' },
  TRANSFER: { text: '转办', tone: 'info' },
  COMPLETE: { text: '完成', tone: 'success' },
  TIMEOUT: { text: '催办', tone: 'danger' },
}

export function msgTypeText(type?: string) {
  if (!type) return '-'
  return MSG_TYPE[type]?.text || type
}

export function msgTypeTone(type?: string) {
  return MSG_TYPE[type || '']?.tone || 'info'
}
