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
| `REJECTED` | 已驳回（流程已终止） | 驳回并终止 | 否，属于最终关闭状态 |
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
| `TicketService.submit` | 仅 `DRAFT` 可提交。调现有 `start`：`processKey` 来自类型、`businessKey=ticket_no`、`formData`、当前登录用户。成功后写 `process_inst_id`，`status=IN_APPROVAL`。与 `start` 同一事务，失败工单仍为草稿。 |
| `TicketService.updateDraft` | 仅 `DRAFT` 和流程内「发起人重新提交」节点允许改业务字段；`REJECTED` 是最终状态，不能修改后再提交。 |
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
5. 另开一张工单提交，待办里选**驳回并终止**（不要选回退节点）：工单变为「已驳回」，不可再编辑或再次提交。
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
- 行按钮：查看、编辑（仅 DRAFT）、提交（仅 DRAFT）、删除（草稿可在列表删除；发起人重新提交状态可在详情删除）。
- 人员字段（`user` / `users`）存的是用户 id，列表按 `GET /system/users/by-ids` 批量换成人名再渲染，缓存在 `utils/userNames.ts`。选人下拉的 `/users/simple` 有 50 条上限，已选的人不在里面时同样按 id 补一条选项，避免只读详情露出数字。

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
5. 草稿可编辑/提交/删除；审批中通常只能查看，退回到「发起人重新提交」时发起人可修改、重新提交或删除。

## 详情配置（第 5 步）

没有页面画布，用 **区块 + 字段多选**。控件类型仍读 `tk_form_ui`，详情 schema 不存第二份 type。

- 表 `tk_detail_ui`（`type_id`, `schema` jsonb, `version`）。每次保存 `version` +1。
- 打开：工单类型 → **配置详情**，路由 `/ticket-types/{id}/detail`。
- 运行时：列表「查看」仍进 `/tickets/{typeCode}/{id}`，按 `sections` 分组渲染。
- `showTimeline=true` 且有 `process_inst_id` 时右侧展示 `ApprovalTimeline`。
- 可编：仅 `DRAFT` 和流程内「发起人重新提交」节点（表单字段）。`IN_APPROVAL` / `APPROVED` / `REJECTED` 全只读。新建草稿不再填写主表「标题」，后端默认写成「类型名 + 工单号」，给待办/流程实例摘要用。列表和详情仍可通过配置展示该列。
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
- 「我的待办 / 我的已办 / 我发起的 / 抄送我的」是所有登录用户都有的弱菜单，不附带工单数据权限。
- 审批角色决定“任务分给谁”，用户角色的 `ALL / DEPT / SELF` 决定“这个人能否读取和办理工单”。当前审批人不在数据范围时仍能进入页面，但服务端返回空业务字段并禁止同意、驳回、转办和保存节点字段；授权后刷新即可恢复。

### 部门经理看本部门工单、看不到总部

1. 角色管理把「部门经理」数据范围改成 **本部门 DEPT**（新库种子角色 `MANAGER` 已是 DEPT）。
2. 该账号挂在销售部（不要挂总部）。
3. 列表/详情只能看到销售部（及销售部下级）同事发起的工单；总部发起的会 403。
4. 若该经理被审批角色选中、但用户角色数据范围不包含发起人，他仍能看到待办，进入后字段为空且不能审批。

### 销售只能看自己的请假工单

1. 角色管理里已有示例角色 `SALES`（数据范围 SELF）。没有则新建：编码 `SALES`，数据范围选「仅自己」。
2. 菜单权限勾选：工作台、审批中心（如需办待办）、**请假**（`/tickets/LEAVE`，保存类型后自动出现）、以及按钮「新建/编辑/提交/删除工单」。不要勾「工单类型」「工单总表」除非你希望他改配置。
3. 用户管理把销售账号的角色改成 `SALES`，重新登录。
4. 侧栏只出现请假列表；列表里只能看到自己发起的单。别人的请假单详情会 403。
5. 若该用户同时是审批角色成员，待办入口始终可见；是否能看字段和审批仍由上述数据范围决定。

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

## 工单详情直接审批（第 8 步）

审批人不用再绕回「我的待办」，工单详情自己判断当前用户在这张单上有没有待办。

| 位置 | 做什么 |
|------|--------|
| `GET /runtime/instances/{processInstanceId}/my-task` | 返回当前用户在该实例上的待办 `{taskId, taskName, resubmitTask}`，没有则 `data` 为 null。 |
| `ProcessRuntimeService.isInvolved` | 处理过或当前待办的人算参与人，可以进入页面；`TicketDataAccessService` 再按用户角色数据范围决定是否返回业务字段和允许办理。 |
| `ProcessRuntimeService.hasResubmitTask` | 被退回到发起人节点时实例还在跑、工单仍是 `IN_APPROVAL`，`updateDraft` 靠它放行改单。 |
| `components/ApprovalActions.vue` | 同意 / 驳回（回退节点或终止）/ 转办 / 前加签，复用 `/runtime/approve|reject|transfer|add-sign`，与待办页行为一致。 |
| `TicketDetailView` | 有待办时顶部出按钮和提示条；重新提交前先 `PUT` 存表单再调 approve；办完刷新工单、轨迹和待办状态。 |

### 前加签

- 当前审批人可点击「加签」，从系统启用用户中选择一个或多个加签人，并填写选填说明。
- `POST /runtime/add-sign` 会保留原审批任务、暂时移除其办理人，并创建关联同一流程实例的加签子任务；原审批人加签期间看不到该待办。
- 加签人只能执行「完成加签」，不能驳回、转办或继续加签。多人加签需全部完成，最后一个完成后原任务自动恢复给发起加签的审批人，再由其决定同意或驳回。
- 加签任务继续遵守工单数据权限：没有该工单数据权限时能看到待办，但字段为空且不能完成加签。加签发起和完成均写入审批轨迹，并发送站内消息。

### 验证

1. `zhangsan` 提交一张工单，用经理账号直接打开该工单详情（不走待办）：顶部提示「当前节点【xx】待您审批」，出现同意/驳回/转办。
2. 点同意，页面就地刷新：按钮消失，轨迹多一节，工单状态按流程推进。
3. 驳回选「回退到发起人」：发起人打开详情看到黄色提示条，表单可编辑，按钮变成「重新提交」，改完提交后轨迹里是「重新提交」而不是「通过」。
4. 与本单无关的人打开详情仍按数据范围拦截（403）。

## 审批节点填写业务字段（第 9 步）

- 流程设计器选中用户任务后，在「本节点填写字段」中勾选可填写字段，并可进一步勾选「必填」。
- 配置保存在该用户任务 `documentation` JSON 的 `writableFields` / `requiredFields`。旧流程没有配置时，审批中保持全部只读；修改后必须重新发布流程。
- `BpmnEnhanceUtil.enhance` 会把审批人配置翻译成监听器后删掉 `documentation`，但保留精简后的字段配置，运行时才能从实例实际部署的版本读出权限。
- 节点可填字段单独渲染成「本节点填写」区块，用最新已发布表单 schema：这些字段可能是工单创建之后才加的，锁定的 `schema_version` 里没有。详情配置里若也勾了同一字段，会从原区块移除避免重复。
- 工单详情始终展示详情配置中的字段。当前节点处理人仅能编辑 `writableFields`，其他字段和其他用户均为只读。
- 字段按归属节点分阶段露出：勾进某个节点 `writableFields` 的字段就归属该节点，没被任何节点勾选的字段属于创建环节。
  - 新建/编辑草稿只显示创建环节字段，`GET /ticket/types/{id}/node-fields` 返回该类型绑定流程里的全部节点字段用于隐藏。
  - 已发起的工单按流程实际走过的节点判断：`GET /ticket/tickets/{id}/field-access` 用 `HistoricActivityInstance` 算出已到达节点，其字段可见，未走到的节点字段（`hiddenFields`）不显示，因此后续环节才录入的内容不会提前暴露给发起人和前置审批人。分支未走到的节点字段同样不显示。
  - 已走过节点的字段若没被详情配置勾选，会补在「审批节点信息」区块只读展示，避免审批完成后节点录入的数据看不到。
  - 工单列表的列宽配置按最小宽度使用：列少时表格撑满容器、「操作」列贴在最右，列多时表格内部横向滚动且「操作」列吸附右侧；单元格单行显示，超长内容悬停查看。
  - 单选、多选、下拉字段存的是选项 value，列表单元格按已发布表单 schema 的选项换成 label 展示；schema 里查不到的值原样显示。
  - 栅格、卡片、标签页等布局组件只有 `name` 没有 `field`，不写入 `tk_field`，因此不会出现在「本节点填写字段」候选里；字段被隐藏后空掉的布局容器在渲染时一并去掉，不会留下空白分组。旧数据里已存入的布局条目在重新保存一次表单设计后自动清除。
- `PATCH /ticket/tickets/{id}/approval-fields` 只接受当前任务白名单字段，越权字段返回 403；保存后同步 `tk_ticket.form_data`、流程变量及 `wf_process_instance_ext.form_data`。
- 点「同意」前自动保存节点数据；后端再次校验 `requiredFields`，缺失时不允许完成任务。
- 工单类「我的待办」会跳转工单详情办理。会签节点共用一份工单数据，后保存覆盖同字段的先保存值。

### 验证

1. 事件工单流程的第一个审批节点勾选「处理结果」「处理说明」，其中只把「处理结果」设为必填，保存并重新发布。
2. 新建草稿：表单里看不到「处理结果」「处理说明」，只有创建环节字段。
3. 发起人提交后查看详情：流程还没走到该节点前，这两个字段不显示；走到之后对非处理人只读展示。
4. 当前处理人从「我的待办」点办理，应跳到工单详情；仅上述两个字段可输入，其他字段只读。
5. 「处理说明」留空可以保存；「处理结果」留空点同意应报必填。填写后同意成功。
6. 同意后再看详情：这两个字段仍在（详情配置没勾时落在「审批节点信息」区块），显示刚才填的值；流程结束后同样可见。
7. 用非当前处理人直接调用节点保存或办理接口，应返回 403；提交一个不在白名单中的字段也应返回 403。

## 审批角色与组织过滤

- 用户角色（`sys_role`）只管理菜单权限和 `ALL / DEPT / SELF` 数据范围；审批角色使用 `wf_approval_role`，成员子表为 `wf_approval_role_user`，管理入口位于「流程设计 / 审批角色」。
- 审批角色编码创建后不可修改。新流程使用独立的 `approvalRole` 审批人类型且设计器只列启用的审批角色；改造前已发布的 `role` 节点继续按旧系统角色解析，但再次编辑发布时必须改选审批角色。
- 新配置的角色节点必须从四种部门过滤中选择：发起人同部门、发起人上级所有部门、发起人同部门及上级所有部门、固定指定部门。指定部门支持多选并在流程设计器直接选择，不读取表单字段；审批角色的成员添加同样支持一次多选多个系统用户。旧流程未配置范围时后端仍按不限制部门兼容，但重新发布前设计器会要求补选。
- 解析顺序是审批角色成员 → 启用系统用户 → 按成员 `dept_id` 过滤。当前用户模型只有一个 `dept_id`，因此兼职多部门暂不支持。
- 下一节点没有匹配人员时，任务创建监听器抛出明确业务异常；Flowable 事务回滚，流程仍停在上一节点。管理员维护审批角色成员后，原处理人再次提交即可继续，不会回退给发起人。
- 审批角色只产生待办，不授予数据权限。当前办理人不在该工单的数据范围时，工单详情、任务详情、实例详情及附件接口都不返回业务数据，页面显示提示并禁用办理动作；后端同样拒绝保存字段、同意、驳回和转办，不能靠直接调用接口绕过。
- 工单只允许在草稿状态，或流程退回到「发起人重新提交」且由发起人本人操作时删除。删除重新提交工单会同时终止活动流程并把流程扩展记录标为 `CANCELLED`；其他状态一律拒绝。

### 验证

1. 新建审批角色并选择不同部门的系统用户作为成员；流程节点选择该角色和「发起人同部门」，发布流程。
2. 不同部门用户分别发起，确认任务只分给审批角色中与发起人同部门的成员。依次验证上级部门、同部门及上级部门和固定指定部门。
3. 临时移除所有匹配成员，上一节点提交应提示找不到审批人且待办仍停在原节点；补回成员后再次提交成功。
4. 给审批人只保留弱菜单、数据范围为 `SELF`，审批别人发起的工单：待办可见，详情字段为空且按钮不可用。把其用户角色改为可覆盖发起人的 `DEPT` 或 `ALL`，刷新页面后字段和办理按钮恢复。

## 独立明细工单与子流程

- 明细复用 `tk_ticket` 存储，通过 `type_id` 使用自己的表单、字段、编号和流程；`parent_ticket_id` 指向主单，`type_relation_id` 区分同一主单下的多种明细。
- `tk_type_relation` 配置主类型与明细类型、显示名、关系编码、排序、是否级联删除、条数范围，以及是否在主单发起时校验下限。一个主类型可以配置多种明细；关系已有数据后只能停用，不能物理删除。
- 明细默认创建为 `DRAFT`，必须在主单子表中手动点击「提交」才启动该明细类型绑定的子流程。驳回并终止后是 `REJECTED` 最终状态，不允许再次提交。
- 主单子表里只能改 `DRAFT` 明细。明细一提交就交给自己的子流程：后续修改只能由明细当前处理人在明细详情页进行，且仅限该子流程节点的可填字段，主单节点的明细列白名单管不到它。
- 主流程用户任务的 `documentation` 增加 `detailConfigs`：
  - 按明细种类控制本节点是否显示、是否允许追加、修改和删除草稿。
  - `writableFields` / `requiredFields` 控制该节点可改和必填的明细列；服务端按白名单合并，不能越权改其它列。
  - 未到达节点配置的明细种类按阶段隐藏；走到后保持只读展示。
  - `minRows` / `maxRows` 限制条数，`0` 表示沿用明细关系上的设置，关系上也是 `0` 表示不限。
  - 设计器不会因为取消勾选而清掉其它配置，重新勾回来原来的列白名单和条数还在；服务端读取时统一归一：本节点不显示的明细一律没有追加、修改、删除权限，既不能追加也不能修改时列白名单按空处理，必填列和条数下限也不再校验。
- 条数校验：上限在新增明细时拦截（前端同时置灰「新增明细」按钮），发起阶段同样生效。下限默认只在审批节点点「同意」时校验，取值按「节点值优先、否则用关系值」。关系勾了 `check_min_on_start` 时，主单发起提交也会按关系下限拦截。`CANCELLED` 的明细不计入条数。
- 同意前关闭校验使用 `childValidationMode`：`NONE` 不校验，`ALL` 校验该主类型全部启用明细关系，`SELECTED` 校验设计器多选的关系。每种仍检查该种类全部明细行。
- `APPROVED`、`REJECTED`（驳回并终止）、`CANCELLED` 算关闭；`DRAFT`、`IN_APPROVAL`（包括退回发起人或指定节点）算未关闭。按关系排序逐种执行 EXISTS 查询，第一种未关闭就返回「{明细类型}未全部关闭」，不继续查其它种类。
- 新增、修改、提交明细与主节点同意都先锁主单，避免关闭校验刚通过时并发产生或提交新的未关闭明细。
- 删除主单时：`cascade_delete=1` 的子单先终止活动子流程再软删除；非级联关系只清空父单和关系引用，子单保留。

## PostgreSQL

默认 profile 仍是 `h2`，避免没装库时起不来。切 PG 的改法见仓库根目录 `README.md`「切换 PostgreSQL」。  
`schema.sql` 已去掉 H2 专用的 `CLOB`，改用 `TEXT`。业务 SQL 里的 `LIMIT 1` 两边都支持。工单扩展字段计划用 `jsonb`（H2 无 jsonb，工单表从第 1 步起按 PG 设计）。

## 系统日志

- `sys_login_log` 记录登录成功和失败（包括用户不存在、停用、密码错误），保存账号、姓名、租户、IP、客户端类型和结果；不保存密码或 Token。
- 客户端类型取请求头 `X-Client-Type`（`WEB` / `APP`），没带则按 User-Agent 推断：含 okhttp、CFNetwork、Dart、Flutter、uni-app 记为 `APP`，含 Mozilla 记为 `WEB`，其余记为 `OTHER`。页面分别显示 CRM / APP / 其他，完整 User-Agent 放在悬停提示里。
- `sys_oper_log` 通过控制器切面记录 `POST / PUT / PATCH / DELETE` 写操作，保存模块、操作类型、操作名称、操作人姓名、账号、请求地址、脱敏参数、结果、异常、耗时和来源。判断前缀时会先剥掉 `server.servlet.context-path`（`/api`），否则模块恒为「其他」且排除规则失效。查询和附件下载不记录；登录已由 `sys_login_log` 记录故不重复写，删除日志、清理历史日志属于写操作会照常留痕。模块按接口一级前缀归类，取值为认证、系统管理、工单、流程运行、流程设计、消息、开放接口、其他，列表页支持按它筛选；操作类型是模块之下更细的分类，取不到时回落到模块值。操作类型对工单业务写具体类型名（如事件工单），对其余接口按归属写用户管理、部门管理、角色管理、菜单管理、工单类型、流程设计、表单管理、流程运行、消息、开放接口等。
- 参数名包含 `password`、`secret`、`token` 时替换为 `******`，参数最多保留 2000 字，错误最多保留 1000 字。
- 两类日志按租户隔离查询；菜单位于「系统管理 / 系统日志」，查看权限为 `sys:log`。
- 删除所选日志和按截止时间清理历史日志仅允许 `admin_flag=1` 的系统管理员调用，普通持有 `sys:log` 权限的角色只能查看。
