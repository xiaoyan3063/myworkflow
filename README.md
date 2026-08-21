# MyWorkflow

独立审批流平台：Spring Boot + Flowable + Vue 3 + Element Plus + PostgreSQL。

业务系统（如 CRM）只负责工单；本系统负责流程设计、组织审批人解析、待办办理与状态回写。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 8 / Spring Boot 2.7 / Flowable 6.8 / MyBatis-Plus / JWT |
| 前端 | Vue 3 / Vite / Element Plus / bpmn-js |
| 数据库 | 开发默认 H2；生产使用 PostgreSQL |

## 功能一览

- 流程设计器（BPMN）与发布
- 表单设计器（JSON Schema）
- 发起 / 待办 / 已办 / 抄送 / 转办 / 驳回
- 组织：用户、部门、角色（审批人可按用户/角色/部门/发起人解析）
- 站内消息 + 超时催办定时任务
- 多租户字段预留（`tenant_id`）
- 开放 API（AppKey/AppSecret）供 CRM 等系统接入

## 快速启动

### 1. 后端

本机 Java 8 的证书库缺少部分根证书，直接 `mvn spring-boot:run` 会报 PKIX 错误。
让 Java 改用 Windows 系统根证书即可解决。

CMD：

```bat
cd backend
run.bat
```

PowerShell：

```powershell
cd backend
$env:MAVEN_OPTS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
mvn -s maven-settings.xml spring-boot:run
```

默认使用 `h2` profile，无需先配数据库。

- API 根路径：http://localhost:8080/api
- Knife4j 文档：http://localhost:8080/api/doc.html
- 演示账号：`admin` / `admin123`（另有 `manager`、`zhangsan`，密码相同）

### 2. 前端

```bash
cd frontend
npm install
npm run dev
```

访问：http://localhost:5173

### 3. 切换 PostgreSQL

依赖里已有 `postgresql` 驱动。默认 `spring.profiles.active` 仍是 `h2`。

1. 本机先建空库（表由启动时的 `schema.sql` + Flowable 自动创建）：

```sql
CREATE DATABASE myworkflow;
```

2. 改 `backend/src/main/resources/application.yml`：

- 文件开头：`spring.profiles.active: postgres`
- `on-profile: postgres` 那段：把 `url` / `username` / `password` 改成你的实例

或不改默认 profile，启动时指定：

```bat
run.bat -Dspring-boot.run.profiles=postgres
```

```powershell
$env:MAVEN_OPTS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
mvn -s maven-settings.xml spring-boot:run "-Dspring-boot.run.profiles=postgres"
```

3. 启动后 `DataInitializer` 执行 `schema.sql`（`CREATE TABLE IF NOT EXISTS`，已有表会跳过），再插入演示账号和演示流程。Flowable 的 `ACT_*` 表由 `flowable.database-schema-update=true` 按 PG 方言创建。

注意：

- 大字段用 `TEXT`，不要用 H2 的 `CLOB`（PostgreSQL 不认）。
- H2 默认大小写不敏感；PostgreSQL 未加引号的列名是小写。本仓库建表已是小写蛇形，可直接用。
- 切到 PG 后数据在磁盘上，重启不会像 H2 那样清空。
- 工单低代码约定见 `docs/ticket-lowcode.md`。工单表单设计器：工单类型页点「设计表单」，schema 在表 `tk_form_ui`。

## 开放接口（对接 CRM）

Header：

```
X-App-Key: crm_demo_key
X-App-Secret: crm_demo_secret_change_me
```

发起审批：

```http
POST /api/openapi/v1/process/start
Content-Type: application/json

{
  "processKey": "leave_approve",
  "businessKey": "CRM-WO-10086",
  "businessType": "leave",
  "title": "张三请假 2 天",
  "starterId": "3",
  "starterName": "张三",
  "formData": {
    "days": 2,
    "reason": "回家"
  }
}
```

按业务单号查询：

```http
GET /api/openapi/v1/process/by-business?businessKey=CRM-WO-10086
```

## 推荐演示路径

1. 使用 `zhangsan` 登录 → 发起审批 → 选择「请假审批」
2. 使用 `manager` 登录 → 待办办理 → 同意
3. 使用 `admin` 登录 → 待办办理 → 同意
4. 回到「我发起的」查看状态为 `COMPLETED`

## 目录结构

```
myworkflow/
├── docs/ticket-lowcode.md   # 工单低代码约定
├── backend/                 # Spring Boot 服务
│   └── src/main/java/com/myworkflow/
│       ├── module/auth      # 登录认证
│       ├── module/system    # 用户部门角色
│       ├── module/process   # 流程定义与运行
│       ├── module/notify    # 消息通知
│       └── module/openapi   # 外部系统 API
└── frontend/                # Vue 管理端
    └── src/views/
        ├── process/         # 流程/表单设计器
        ├── task/            # 审批中心
        └── system/          # 组织权限
```

## 说明

- 当前环境为 **Java 8**，故采用 Spring Boot 2.7；若升级到 JDK 17+，可再迁移至 Spring Boot 3。
- 会签/或签：可通过 BPMN 多实例节点配置；设计器属性面板已预留说明，发布时会注入审批人监听器。
- 邮件通知默认关闭（`myworkflow.notify.mail-enabled=false`），站内信默认开启。
