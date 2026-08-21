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

export function rulesFromSchema(schema: any): any[] {
  if (Array.isArray(schema?.raw) && schema.raw.length) {
    return dropEmptyOptions(schema.raw)
  }
  return (schema?.fields || []).map((f: any) => {
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
}

function toFcType(type: string) {
  if (type === 'textarea') return 'textarea'
  if (type === 'number') return 'inputNumber'
  if (type === 'select') return 'select'
  if (type === 'date') return 'datePicker'
  if (type === 'user') return 'TicketUserSelect'
  if (type === 'users') return 'TicketUsersSelect'
  return 'input'
}

export const silentFormOption = {
  submitBtn: false,
  resetBtn: false,
  form: { labelWidth: '100px' },
}
