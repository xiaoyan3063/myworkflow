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

## 对接审批流（第 3 步）

工单是主数据，`ProcessRuntimeService.start / approve / reject / timeline` 行为不变，只在流程**真正结束**时回写工单。

### 改了哪些方法

| 位置 | 做什么 |
|------|--------|
| `TicketService.submit` | 仅 `DRAFT` / 终止后的 `REJECTED` 可提交。调现有 `start`：`processKey` 来自类型、`businessKey=ticket_no`、`formData`、当前登录用户。成功后写 `process_inst_id`，`status=IN_APPROVAL`。与 `start` 同一事务，失败工单不变审批中。 |
| `TicketService.updateDraft` | `IN_APPROVAL` / `APPROVED` 禁止改业务字段；`REJECTED` 可改后再提交。 |
| `TicketService.ticketPage` / `ticketDetail` | 补 `currentApprover`（复用 `ProcessRuntimeService.currentApprovers`）。 |
| `POST /ticket/tickets/{id}/submit` | 提交入口。开放 API 不循环调用。 |
| `ProcessRuntimeService.refreshInstanceStatus` | 实例走完 `COMPLETED` 时回调。 |
| `ProcessRuntimeService.rejectAndTerminate` | 终止驳回 `REJECTED` 时回调。回退节点（实例仍 `RUNNING`）**不**回调。 |
| `ProcessFinishListener` / `TicketProcessCallback` | 用 `businessKey=ticket_no` 匹配工单；匹配不到只打日志（兼容「发起审批」纯流程单）。 |

回写：`COMPLETED` → 工单 `APPROVED`；终止 `REJECTED` → 工单 `REJECTED`。

### 测试步骤（绑 leave_approve）

1. 流程管理确认 `leave_approve` 已发布。工单类型把「绑定流程」填成 `leave_approve`，并设计好表单。
2. 用 `zhangsan` 建草稿、保存、提交。工单应变为「审批中」，出现 `process_inst_id`；`wf_process_instance_ext.business_key` 等于工单号。列表「当前审批人」有人。
3. 打开工单详情：表单只读，右侧有 `ApprovalTimeline`。此时编辑按钮应不可用。
4. 用经理账号在待办通过：流程结束后工单变为「已通过」，轨迹显示结束。
5. 另开一张工单提交，待办里选**驳回并终止**（不要选回退节点）：工单变为「已驳回」，可再编辑并再次提交。
6. 再开一张，驳回并**回退到发起人**：流程仍在跑，工单仍是「审批中」，不变成已驳回。发起人在待办里重新提交即可，不要再点工单「提交」（避免叠第二实例）。
7. 菜单「发起审批」走原来的请假单，不应报错，日志可出现 `no ticket for businessKey=... skip writeback`。


### 验证

1. 新建工单类型，打开设计表单，拖入单行/多行/数字/下拉/日期/人员单选/人员多选，保存。
2. 看库：`tk_form_ui.schema.raw` 有内容，`version` 递增；`tk_field` 有对应 `field_key`，再保存 ID 不变。
3. 工单列表新建草稿，表单与设计一致，保存后再次打开值还在。
4. 旧草稿缺新字段时显示空，不报错。

## 列表配置（第 4 步）

没有列表画布，用 **字段勾选** 配列和筛选。不改审批流核心。

- 表 `tk_list_ui`（`type_id`, `schema` jsonb, `version`）。每次保存 `version` +1。
- 打开：工单类型 → **配置列表**，路由 `/ticket-types/{id}/list`。
- 运行时：`/tickets/{typeCode}`（如 `/tickets/LEAVE`）。查看跳 `/tickets/{typeCode}/{id}`。
- 旧 `/tickets` 总表仍可用。纯数字的旧详情 URL 会尝试重定向到带 typeCode 的路径。

查询：`GET /ticket/tickets/by-type/{typeCode}?page=1&size=10&status=DRAFT&days=3`

- 只有 `schema.filters` 里出现过的 `field` 会进 WHERE。
- `status` / `ticket_no` / `title` / `createTime` 走主列；扩展字段 `form_data->>'key'`（字段名必须是 `[a-zA-Z][a-zA-Z0-9_]*`）。
- 分页仍是 `PageResult`。
- 状态用 `TICKET_STATUS`（草稿/审批中/已通过/已驳回），不和流程 `RUNNING` 混用。
- 行按钮：查看、编辑（仅 DRAFT/REJECTED）、提交、删除草稿。本步全部显示。

### 配置 JSON 示例

```json
{
  "version": 1,
  "designer": "checkbox",
  "raw": null,
  "columns": [
    { "field": "ticket_no", "title": "工单号", "width": 180, "from": "main" },
    { "field": "title", "title": "标题", "width": 200, "from": "main" },
    { "field": "status", "title": "状态", "width": 100, "from": "main" },
    { "field": "type", "title": "请假类型", "width": 140, "from": "json" },
    { "field": "days", "title": "请假天数", "width": 120, "from": "json" },
    { "field": "createTime", "title": "创建时间", "width": 180, "from": "main" }
  ],
  "filters": [
    { "field": "ticket_no", "op": "like", "from": "main" },
    { "field": "status", "op": "eq", "from": "main" },
    { "field": "type", "op": "eq", "from": "json" },
    { "field": "days", "op": "gte", "from": "json" }
  ],
  "rowActions": ["view", "edit", "submit", "delete"]
}
```

### 筛选 SQL 示例

对应 `GET /ticket/tickets/by-type/LEAVE?status=APPROVED&days=3`：

```sql
SELECT * FROM tk_ticket
WHERE deleted = 0
  AND type_id = :typeId
  AND status = 'APPROVED'
  AND form_data->>'days' >= '3'
ORDER BY create_time DESC
LIMIT 10 OFFSET 0;
```

未出现在 `filters` 里的参数（包括任意 jsonb 路径）会被忽略，不会拼进 SQL。

### 验证

1. 重启后端，确认 `tk_list_ui` 已建。
2. 工单类型 → 配置列表：勾选主表列 + 若干扩展字段，保存。
3. 打开 `/tickets/LEAVE`，列和筛选与配置一致，状态显示中文。
4. 用已通过 + 扩展字段筛选，结果对；多传一个未配置参数结果不变。
5. 草稿可编辑/提交/删除；审批中只能查看。

## 详情配置（第 5 步）

没有页面画布，用 **区块 + 字段多选**。控件类型仍读 `tk_form_ui`，详情 schema 不存第二份 type。

- 表 `tk_detail_ui`（`type_id`, `schema` jsonb, `version`）。每次保存 `version` +1。
- 打开：工单类型 → **配置详情**，路由 `/ticket-types/{id}/detail`。
- 运行时：列表「查看」仍进 `/tickets/{typeCode}/{id}`，按 `sections` 分组渲染。
- `showTimeline=true` 且有 `process_inst_id` 时右侧展示 `ApprovalTimeline`。
- 可编：仅 `DRAFT` / `REJECTED`（标题 + 表单字段）。`IN_APPROVAL` / `APPROVED` 全只读。
- `actions`：`save` / `submit` / `cancel` 只控制是否露出按钮，能不能点仍看工单状态。新建/编辑弹窗继续用 `TicketForm`。

### schema 示例

```json
{
  "version": 1,
  "designer": "checkbox",
  "raw": null,
  "showTimeline": true,
  "sections": [
    { "title": "基本信息", "fields": ["ticket_no", "title", "status", "starterName", "createTime"] },
    { "title": "申请内容", "fields": ["type", "days", "reson"] }
  ],
  "actions": ["save", "submit"]
}
```

### 验收点

1. 重启后端，确认 `tk_detail_ui` 已建。
2. 配置详情：两个区块分别勾主表字段和表单字段，打开 `showTimeline`，保存。
3. 从 `/tickets/LEAVE` 点查看，详情按区块分组；草稿可改标题和表单并保存、提交。
4. 审批中打开同一页：字段只读，保存/提交按钮不出现，有轨迹则显示。
5. 关掉 `showTimeline` 再保存，详情页不再出现右侧轨迹栏。

## 菜单与权限（第 6 步）

真正的 RBAC：`sys_menu` + `sys_role_menu`，登录返回 `menus` + `perms`。侧栏按菜单生成。`admin_flag=1` 放行全部接口。

- 按钮 perm：`ticket:create` / `ticket:update` / `ticket:submit` / `ticket:delete` / `ticket:type:save` 等。
- 工单列表数据范围在角色上（按发起人部门，不是按工单所属部门字段）：
  - `ALL`：全部工单
  - `DEPT`：本部门及下级部门同事发起的工单；**不含上级/总部**（销售部经理看不到总部人员发起的单）
  - `SELF`：仅 `starter_id=当前用户`
- 一人多角色时取最宽：`ALL` > `DEPT` > `SELF`。没挂部门时 `DEPT` 退化为仅自己。
- 保存工单类型时自动在「工单」目录挂 `/tickets/{typeCode}`。
- `/runtime/**` **不**校验 `ticket:submit`，待办办理仍只看任务候选人。

### 部门经理看本部门工单、看不到总部

1. 角色管理把「部门经理」数据范围改成 **本部门 DEPT**（新库种子角色 `MANAGER` 已是 DEPT）。
2. 该账号挂在销售部（不要挂总部）。
3. 列表/详情只能看到销售部（及销售部下级）同事发起的工单；总部发起的会 403。
4. 待办审批别人的单仍走 `/runtime/**`，不受此范围限制。

### 销售只能看自己的请假工单

1. 角色管理里已有示例角色 `SALES`（数据范围 SELF）。没有则新建：编码 `SALES`，数据范围选「仅自己」。
2. 菜单权限勾选：工作台、审批中心（如需办待办）、**请假**（`/tickets/LEAVE`，保存类型后自动出现）、以及按钮「新建/编辑/提交/删除工单」。不要勾「工单类型」「工单总表」除非你希望他改配置。
3. 用户管理把销售账号的角色改成 `SALES`，重新登录。
4. 侧栏只出现请假列表；列表里只能看到自己发起的单。别人的请假单详情会 403。
5. 该用户在待办里审批别人的单不受影响（走 `/runtime/**`）。

## 附件、编号、配置发布（第 7 步）

- 附件表 `tk_ticket_file`。`POST /ticket/files` 上传，返回 `id`；表单 file 字段存 **id 数组**。下载 `GET /ticket/files/{id}`（带 JWT）。设计器左侧有「附件」控件，内置 upload 在运行时也会改成同一组件。
- 工单号：类型上 `no_prefix` + `no_date_pattern` + `no_seq_len`，创建草稿时生成。提交审批时若没有 `ticket_no` 会拒绝（并补一次生成）。
- `tk_form_ui` / `tk_list_ui` / `tk_detail_ui` 的 `status`：`DRAFT` 草稿 / `PUBLISHED` 已发布。设计器「保存草稿」只写 DRAFT；「发布」插入一条 PUBLISHED。运行时 `?published=true`。
- 创建工单时把当时已发布表单的 `version` 写入 `tk_ticket.schema_version`。详情打开表单按这个版本，不受之后再发布影响。
- 类型绑定的 `processKey` 必须在 `wf_process_def` 且 `status=1`（已发布）。保存类型、提交审批都会校验。审批待办逻辑未改。

### 验证清单

1. 重启后端，确认 `tk_ticket_file`、`schema_version`、三类 UI 的 `status`、类型编号字段已加上。已有表单配置会被标成 PUBLISHED。
2. 工单类型：编号前缀/日期/流水保存后，新建草稿工单号为 `前缀-日期-流水`。把绑定流程改成未发布的 key，保存应失败。
3. 设计表单拖入「附件」，保存草稿。此时运行时新建工单仍是上一版（或旧数据）。点发布后再新建，表单出现附件；上传后 `form_data` 里是数字 id 列表，库里 `tk_ticket_file` 有行。
4. 再改表单并只保存草稿、不发布：新开的工单仍是已发布版。已在途工单详情字段与创建时版本一致。
5. 列表/详情同样：草稿不进运行时，发布后列表列才变。
6. 提交一张没有工单号的单（正常不会发生）应报「提交审批前必须已有工单号」。绑定流程停用后提交应报未发布。
7. 待办通过/驳回工单状态回写与以前相同。

## PostgreSQL

默认 profile 仍是 `h2`，避免没装库时起不来。切 PG 的改法见仓库根目录 `README.md`「切换 PostgreSQL」。  
`schema.sql` 已去掉 H2 专用的 `CLOB`，改用 `TEXT`。业务 SQL 里的 `LIMIT 1` 两边都支持。工单扩展字段计划用 `jsonb`（H2 无 jsonb，工单表从第 1 步起按 PG 设计）。
