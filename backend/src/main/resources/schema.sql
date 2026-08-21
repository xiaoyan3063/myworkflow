-- 业务表结构（Flowable 表由引擎自动创建）
-- 同时兼容 H2 与 PostgreSQL：
--   * 不用 CLOB（H2 专有，PG 请用 TEXT）
--   * 不用 AUTO_INCREMENT / IDENTITY（主键由 MyBatis-Plus 雪花 ID 写入）
--   * 未加引号的标识符在 PG 中会折成小写，故列名一律小写蛇形
-- 启动时由 DataInitializer 执行；已存在的表会因 IF NOT EXISTS 跳过，不会做迁移

CREATE TABLE IF NOT EXISTS sys_tenant (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0,
    tenant_code     VARCHAR(64) NOT NULL,
    tenant_name     VARCHAR(128) NOT NULL,
    status          INT DEFAULT 1,
    remark          VARCHAR(512),
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    create_by       BIGINT,
    update_by       BIGINT,
    deleted         INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_dept (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0,
    parent_id       BIGINT DEFAULT 0,
    dept_name       VARCHAR(128) NOT NULL,
    dept_code       VARCHAR(64),
    sort_no         INT DEFAULT 0,
    status          INT DEFAULT 1,
    leader_id       VARCHAR(64),
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    create_by       BIGINT,
    update_by       BIGINT,
    deleted         INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0,
    username        VARCHAR(64) NOT NULL,
    password        VARCHAR(128) NOT NULL,
    real_name       VARCHAR(64),
    email           VARCHAR(128),
    mobile          VARCHAR(32),
    dept_id         BIGINT,
    avatar          VARCHAR(512),
    status          INT DEFAULT 1,
    admin_flag      INT DEFAULT 0,
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    create_by       BIGINT,
    update_by       BIGINT,
    deleted         INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_role (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0,
    role_code       VARCHAR(64) NOT NULL,
    role_name       VARCHAR(128) NOT NULL,
    sort_no         INT DEFAULT 0,
    status          INT DEFAULT 1,
    remark          VARCHAR(512),
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    create_by       BIGINT,
    update_by       BIGINT,
    deleted         INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    role_id         BIGINT NOT NULL,
    tenant_id       BIGINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS wf_process_category (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0,
    category_name   VARCHAR(128) NOT NULL,
    sort_no         INT DEFAULT 0,
    remark          VARCHAR(512),
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    create_by       BIGINT,
    update_by       BIGINT,
    deleted         INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS wf_process_def (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0,
    process_key     VARCHAR(128) NOT NULL,
    process_name    VARCHAR(256) NOT NULL,
    category_id     BIGINT,
    form_id         BIGINT,
    icon            VARCHAR(128),
    description     VARCHAR(1024),
    version         INT DEFAULT 1,
    status          INT DEFAULT 0,
    bpmn_xml        TEXT,
    flowable_deploy_id VARCHAR(64),
    flowable_def_id    VARCHAR(64),
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    create_by       BIGINT,
    update_by       BIGINT,
    deleted         INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS wf_form_def (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0,
    form_key        VARCHAR(128) NOT NULL,
    form_name       VARCHAR(256) NOT NULL,
    form_schema     TEXT,
    status          INT DEFAULT 1,
    remark          VARCHAR(512),
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    create_by       BIGINT,
    update_by       BIGINT,
    deleted         INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS wf_process_instance_ext (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0,
    process_inst_id VARCHAR(64) NOT NULL,
    process_def_id  BIGINT,
    process_key     VARCHAR(128),
    business_key    VARCHAR(128),
    business_type   VARCHAR(64),
    title           VARCHAR(512),
    starter_id      BIGINT,
    starter_name    VARCHAR(64),
    status          VARCHAR(32),
    form_data       TEXT,
    start_time      TIMESTAMP,
    end_time        TIMESTAMP,
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    create_by       BIGINT,
    update_by       BIGINT,
    deleted         INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS wf_cc_record (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0,
    process_inst_id VARCHAR(64),
    task_id         VARCHAR(64),
    user_id         BIGINT,
    title           VARCHAR(512),
    read_flag       INT DEFAULT 0,
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    create_by       BIGINT,
    update_by       BIGINT,
    deleted         INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS wf_notify_message (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0,
    user_id         BIGINT NOT NULL,
    title           VARCHAR(256),
    content         VARCHAR(1024),
    msg_type        VARCHAR(32),
    biz_id          VARCHAR(64),
    read_flag       INT DEFAULT 0,
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    create_by       BIGINT,
    update_by       BIGINT,
    deleted         INT DEFAULT 0
);

-- 工单底座（PostgreSQL jsonb；H2 无 jsonb，工单模块按 PG 使用）
CREATE TABLE IF NOT EXISTS tk_type (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0,
    type_code       VARCHAR(64) NOT NULL,
    type_name       VARCHAR(128) NOT NULL,
    process_key     VARCHAR(128),
    status          INT DEFAULT 1,
    remark          VARCHAR(512),
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    create_by       BIGINT,
    update_by       BIGINT,
    deleted         INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tk_field (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0,
    type_id         BIGINT NOT NULL,
    field_key       VARCHAR(64) NOT NULL,
    title           VARCHAR(128) NOT NULL,
    field_type      VARCHAR(32) NOT NULL,
    required        INT DEFAULT 0,
    list_visible    INT DEFAULT 1,
    sort_no         INT DEFAULT 0,
    options_json    TEXT,
    remark          VARCHAR(512),
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    create_by       BIGINT,
    update_by       BIGINT,
    deleted         INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tk_form_ui (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0,
    type_id         BIGINT NOT NULL,
    version         INT DEFAULT 1,
    schema          JSONB,
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    create_by       BIGINT,
    update_by       BIGINT,
    deleted         INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tk_list_ui (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0,
    type_id         BIGINT NOT NULL,
    version         INT DEFAULT 1,
    schema          JSONB,
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    create_by       BIGINT,
    update_by       BIGINT,
    deleted         INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tk_detail_ui (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0,
    type_id         BIGINT NOT NULL,
    version         INT DEFAULT 1,
    schema          JSONB,
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    create_by       BIGINT,
    update_by       BIGINT,
    deleted         INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tk_ticket (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0,
    type_id         BIGINT NOT NULL,
    ticket_no       VARCHAR(64) NOT NULL,
    title           VARCHAR(512),
    status          VARCHAR(32) NOT NULL,
    starter_id      BIGINT,
    starter_name    VARCHAR(64),
    process_inst_id VARCHAR(64),
    form_data       JSONB,
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    create_by       BIGINT,
    update_by       BIGINT,
    deleted         INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS open_app (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0,
    app_name        VARCHAR(128) NOT NULL,
    app_key         VARCHAR(64) NOT NULL,
    app_secret      VARCHAR(128) NOT NULL,
    status          INT DEFAULT 1,
    callback_url    VARCHAR(512),
    remark          VARCHAR(512),
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    create_by       BIGINT,
    update_by       BIGINT,
    deleted         INT DEFAULT 0
);
