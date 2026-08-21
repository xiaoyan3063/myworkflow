# 工单低代码约定（第 0 步）

后续工单做在审批流之上，但 **工单是主数据，审批是从属动作**。  
本文件只定模型与 JSON 骨架，不绑定具体设计器 API。表单画布准备接入成熟产品（StudioOne 或 Vue 3 + Element Plus 兼容的同类）；包名、组件名、导入路径等 **接入时再按官方文档填写**，此处一律用占位。

## 工单与流程的关系

```
工单 tk_ticket
    │  ticket_no  ────────────►  流程实例 businessKey
    │  process_inst_id  ◄──────  Flowable 实例 ID（提交审批后回写）
    │  form_data        ──────►  start 时拷贝为流程变量（审批快照）
    ▼
流程实例 wf_process_instance_ext + Flowable 运行时
```

- 业务人员日常操作的是工单：新建、保存草稿、改字段、提交审批、看列表。
- 审批流只负责任务怎么走：待办、驳回回退、轨迹。不把 `wf_process_instance_ext.form_data` 当工单主存储。
- 关联键：**`businessKey = ticket_no`（工单号）**。流程实例 ID 只存在工单上，工单列表不要拿实例 ID 当业务主键。
- 提交审批：工单 `DRAFT` → 调现有 `ProcessRuntimeService.start`（`processKey` 来自工单类型绑定、`starterId` 为本系统用户 ID、`formData` 为工单当前字段）。成功后工单写入 `process_inst_id`，状态改为 `IN_APPROVAL`。
- 回写：流程 `COMPLETED` → 工单 `APPROVED`；流程终止驳回 `REJECTED` → 工单 `REJECTED`。驳回并退回发起人、流程仍 `RUNNING` 时，工单保持 `IN_APPROVAL`。
- 同一张工单优先沿用同一流程实例（退回重新提交）。不要用同一个 `ticket_no` 再 `start` 一次叠出第二实例，除非产品明确「再次提交开新流程」。
- 现有 `wf_form_def` 继续服务「纯审批发起」。工单字段主数据走后续的 `tk_field` / 设计器 schema，两套不要混用同一张表硬塞。

## 工单主状态

存在工单表上的 `status`，与流程实例状态分开翻译、分开存。

| 编码 | 含义 | 谁写入 | 此时能否改业务字段 |
|------|------|--------|-------------------|
| `DRAFT` | 草稿 | 创建/保存 | 能 |
| `IN_APPROVAL` | 审批中 | 提交成功 | 否（流程内「发起人重新提交」除外，那是审批待办） |
| `APPROVED` | 已通过 | 流程结束且通过 | 否 |
| `REJECTED` | 已驳回（流程已终止） | 驳回并终止 | 能，改完可再次提交 |
| `CANCELLED` | 已撤销 | 发起人/管理员撤销 | 否 |

流程侧仍用现有：`RUNNING` / `COMPLETED` / `REJECTED` / `CANCELLED`。列表不要直接展示流程英文状态。

## 三种配置 JSON 骨架

运行时只认这三种文档形状。设计器导出后 **映射进这些结构再入库**；不要把设计器私有协议直接当运行时协议，以免换产品时整库作废。

字段稳定键一律用 `field`（对应后续 `tk_field.field_key`），不要用设计器内部的临时 id 当存储键。

### formSchema（表单）

控制新建/编辑时有哪些控件。运行时写入工单 `form_data`：`{ [field]: value }`。

```json
{
  "version": 1,
  "designer": "pending",
  "raw": null,
  "fields": [
    {
      "field": "title",
      "title": "标题",
      "type": "input",
      "required": true,
      "props": {}
    },
    {
      "field": "amount",
      "title": "金额",
      "type": "number",
      "required": false,
      "props": {}
    },
    {
      "field": "reason",
      "title": "事由",
      "type": "textarea",
      "required": false,
      "props": { "rows": 3 }
    },
    {
      "field": "category",
      "title": "类型",
      "type": "select",
      "required": false,
      "options": [{ "label": "折扣", "value": "discount" }],
      "props": {}
    },
    {
      "field": "owner",
      "title": "负责人",
      "type": "user",
      "required": false,
      "props": {}
    }
  ]
}
```

- `designer`：当前为 `FcDesigner`。
- `raw`：设计器原生 rule 数组，二次打开画布用。运行时 **优先读 `raw`，没有再按 `fields` 生成**。
- `type`：`input` / `textarea` / `number` / `select` / `date` / `user` / `users`。

### listSchema（列表）

控制某工单类型的列表列、筛选项、行按钮。`from: main` 读工单主列，`from: json` 读 `form_data`。

```json
{
  "version": 1,
  "designer": "pending",
  "raw": null,
  "columns": [
    { "field": "ticket_no", "title": "工单号", "width": 160, "from": "main" },
    { "field": "title", "title": "标题", "width": 200, "from": "main" },
    { "field": "status", "title": "状态", "width": 120, "from": "main" },
    { "field": "amount", "title": "金额", "width": 120, "from": "json" },
    { "field": "create_time", "title": "创建时间", "width": 180, "from": "main" }
  ],
  "filters": [
    { "field": "ticket_no", "op": "like", "from": "main" },
    { "field": "status", "op": "eq", "from": "main" },
    { "field": "amount", "op": "gte", "from": "json" }
  ],
  "rowActions": ["view", "edit", "submit", "delete"]
}
```

查询 API 只允许 `filters` 里出现过的字段，避免客户端任意拼 jsonb 路径。

### detailSchema（详情 / 弹窗）

只控制「展示哪些、怎么分组、哪些按钮」，控件类型仍以 `formSchema.fields` 为准，不在这里再存一份 type。

```json
{
  "version": 1,
  "designer": "pending",
  "raw": null,
  "showTimeline": true,
  "sections": [
    {
      "title": "基本信息",
      "fields": ["ticket_no", "title", "status"]
    },
    {
      "title": "申请内容",
      "fields": ["amount", "reason", "category", "owner"]
    }
  ],
  "actions": ["save", "submit", "cancel"]
}
```

- `showTimeline`：有 `process_inst_id` 时展示现有审批轨迹组件。
- `actions` 是否可点由工单状态 + 权限共同决定，配置只表示「这个页面要不要露出这个按钮」。

## 表单设计器（第 2 步）

接入 **FcDesigner**（`@form-create/designer` + `@form-create/element-ui`），只做「画表单 + 填表单」。

- 打开：菜单 **工单 → 工单类型 → 设计表单**，路由 `/ticket-types/{id}/form`。
- 存哪：`tk_form_ui.schema`（jsonb）。`raw` 是设计器 rule 数组，`fields` 是解析后的稳定字段列表；每次保存 `tk_form_ui.version` +1。
- 同步：保存时按 `field_key` upsert `tk_field`（已有行保留 ID），画布上删掉的字段会从 `tk_field` 删除。已填工单 `form_data` 不自动迁移，缺键当空。
- 填表：工单新建/编辑用 `TicketForm`，读 `schema.raw` 渲染。人员控件数据源 `/system/users/simple`。
- 不改：BPMN 设计器、`wf_form_def`、审批待办页。

### 验证

1. 新建工单类型，打开设计表单，拖入单行/多行/数字/下拉/日期/人员单选/人员多选，保存。
2. 看库：`tk_form_ui.schema.raw` 有内容，`version` 递增；`tk_field` 有对应 `field_key`，再保存 ID 不变。
3. 工单列表新建草稿，表单与设计一致，保存后再次打开值还在。
4. 旧草稿缺新字段时显示空，不报错。

## PostgreSQL

默认 profile 仍是 `h2`，避免没装库时起不来。切 PG 的改法见仓库根目录 `README.md`「切换 PostgreSQL」。  
`schema.sql` 已去掉 H2 专用的 `CLOB`，改用 `TEXT`。业务 SQL 里的 `LIMIT 1` 两边都支持。工单扩展字段计划用 `jsonb`（H2 无 jsonb，工单表从第 1 步起按 PG 设计）。
