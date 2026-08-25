import uniqueId from '@form-create/utils/lib/unique'

export const ticketUserDragRule = {
  menu: 'main',
  icon: 'icon-select',
  label: '人员单选',
  name: 'TicketUserSelect',
  mask: true,
  input: true,
  validate: ['string'],
  event: ['change'],
  rule() {
    return {
      type: 'TicketUserSelect',
      field: uniqueId(),
      title: '人员',
      info: '',
      $required: false,
      props: { multiple: false },
    }
  },
  props() {
    return [{ type: 'switch', title: '是否禁用', field: 'disabled' }]
  },
}

export const ticketFileDragRule = {
  menu: 'main',
  icon: 'icon-upload',
  label: '附件',
  name: 'TicketFileUpload',
  mask: true,
  input: true,
  validate: ['array'],
  event: ['change'],
  rule() {
    return {
      type: 'TicketFileUpload',
      field: uniqueId(),
      title: '附件',
      info: '',
      $required: false,
      props: {},
    }
  },
  props() {
    return [{ type: 'switch', title: '是否禁用', field: 'disabled' }]
  },
}

export const ticketUsersDragRule = {
  menu: 'main',
  icon: 'icon-select',
  label: '人员多选',
  name: 'TicketUsersSelect',
  mask: true,
  input: true,
  validate: ['array'],
  event: ['change'],
  rule() {
    return {
      type: 'TicketUsersSelect',
      field: uniqueId(),
      title: '人员',
      info: '',
      $required: false,
      props: { multiple: true },
    }
  },
  props() {
    return [{ type: 'switch', title: '是否禁用', field: 'disabled' }]
  },
}

function isBlank(v: any) {
  return v === undefined || v === null || String(v).trim() === ''
}

/** 选项行只清空文字、没删行时会留下 {label:'',value:''}，渲染出来是个空选项 */
export function dropEmptyOptions(rule: any): any {
  if (Array.isArray(rule)) {
    return rule.map(dropEmptyOptions)
  }
  if (!rule || typeof rule !== 'object') {
    return rule
  }
  const out: any = { ...rule }
  if (Array.isArray(out.options)) {
    out.options = out.options.filter(
      (o: any) => !(o && typeof o === 'object' && isBlank(o.label) && isBlank(o.value)),
    )
  }
  if (Array.isArray(out.children)) {
    out.children = out.children.map(dropEmptyOptions)
  }
  return out
}

export function filterRulesByFields(rules: any[], fields?: string[]): any[] {
  if (!fields || !fields.length) return dropEmptyOptions(rules || [])
  const allow = new Set(fields)
  return pickRules(dropEmptyOptions(rules || []), allow)
}

function pickRules(rules: any[], allow: Set<string>): any[] {
  const out: any[] = []
  for (const r of rules || []) {
    if (!r || typeof r !== 'object') continue
    if (r.field && allow.has(r.field)) {
      out.push(r)
      continue
    }
    if (Array.isArray(r.children)) {
      const children = pickRules(r.children, allow)
      if (children.length) out.push({ ...r, children })
    }
  }
  return out
}

export function rulesFromSchema(schema: any, onlyFields?: string[]): any[] {
  if (Array.isArray(schema?.raw) && schema.raw.length) {
    return filterRulesByFields(rewriteFileControls(schema.raw), onlyFields)
  }
  const mapped = (schema?.fields || []).map((f: any) => {
    const type = toFcType(f.type)
    const rule: any = {
      type,
      field: f.field,
      title: f.title,
      $required: !!f.required,
      props: f.props || {},
    }
    if (f.type === 'select' && Array.isArray(f.options)) {
      rule.options = f.options
    }
    if (f.type === 'users') {
      rule.props = { ...(rule.props || {}), multiple: true }
    }
    return rule
  })
  return filterRulesByFields(mapped, onlyFields)
}

function rewriteFileControls(rule: any): any {
  if (Array.isArray(rule)) {
    return rule.map(rewriteFileControls)
  }
  if (!rule || typeof rule !== 'object') {
    return rule
  }
  const out: any = { ...rule }
  const t = String(out.type || '').replace(/-/g, '').toLowerCase()
  if (t === 'upload' || t === 'elupload' || t === 'file') {
    out.type = 'TicketFileUpload'
  }
  if (Array.isArray(out.children)) {
    out.children = out.children.map(rewriteFileControls)
  }
  return out
}

function toFcType(type: string) {
  if (type === 'textarea') return 'textarea'
  if (type === 'number') return 'inputNumber'
  if (type === 'select') return 'select'
  if (type === 'date') return 'datePicker'
  if (type === 'user') return 'TicketUserSelect'
  if (type === 'users') return 'TicketUsersSelect'
  if (type === 'file') return 'TicketFileUpload'
  return 'input'
}

export const silentFormOption = {
  submitBtn: false,
  resetBtn: false,
  form: { labelWidth: '100px' },
}
