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

CREATE TABLE IF NOT EXISTS sys_login_log (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0,
    user_id         BIGINT,
    username        VARCHAR(64),
    real_name       VARCHAR(64),
    status          INT NOT NULL,
    message         VARCHAR(512),
    ip              VARCHAR(64),
    client_type     VARCHAR(16),
    user_agent      VARCHAR(512),
    create_time     TIMESTAMP
);

ALTER TABLE sys_login_log ADD COLUMN IF NOT EXISTS real_name VARCHAR(64);
ALTER TABLE sys_login_log ADD COLUMN IF NOT EXISTS client_type VARCHAR(16);

CREATE INDEX IF NOT EXISTS idx_login_log_tenant_time
    ON sys_login_log (tenant_id, create_time);
CREATE INDEX IF NOT EXISTS idx_login_log_username_time
    ON sys_login_log (username, create_time);

CREATE TABLE IF NOT EXISTS sys_oper_log (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0,
    user_id         BIGINT,
    username        VARCHAR(64),
    real_name       VARCHAR(64),
    module          VARCHAR(64),
    title           VARCHAR(128),
    request_uri     VARCHAR(512),
    http_method     VARCHAR(16),
    oper_param      TEXT,
    status          INT NOT NULL,
    error_msg       VARCHAR(1000),
    cost_ms         BIGINT,
    ip              VARCHAR(64),
    user_agent      VARCHAR(512),
    source          VARCHAR(16) DEFAULT 'WEB',
    ticket_type_name VARCHAR(128),
    create_time     TIMESTAMP
);

ALTER TABLE sys_oper_log ADD COLUMN IF NOT EXISTS ticket_type_name VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_oper_log_tenant_time
    ON sys_oper_log (tenant_id, create_time);
CREATE INDEX IF NOT EXISTS idx_oper_log_username_time
    ON sys_oper_log (username, create_time);

CREATE TABLE IF NOT EXISTS sys_role (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0,
    role_code       VARCHAR(64) NOT NULL,
    role_name       VARCHAR(128) NOT NULL,
    sort_no         INT DEFAULT 0,
    status          INT DEFAULT 1,
    data_scope      VARCHAR(32) DEFAULT 'ALL', -- ALL / DEPT / SELF
    remark          VARCHAR(512),
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    create_by       BIGINT,
    update_by       BIGINT,
    deleted         INT DEFAULT 0
);

ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS data_scope VARCHAR(32) DEFAULT 'ALL';

CREATE TABLE IF NOT EXISTS sys_user_role (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    role_id         BIGINT NOT NULL,
    tenant_id       BIGINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_menu (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0,
    parent_id       BIGINT DEFAULT 0,
    menu_type       VARCHAR(16) NOT NULL,
    menu_name       VARCHAR(128) NOT NULL,
    path            VARCHAR(256),
    icon            VARCHAR(64),
    perm            VARCHAR(128),
    visible         INT DEFAULT 1,
    sort_no         INT DEFAULT 0,
    status          INT DEFAULT 1,
    remark          VARCHAR(512),
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    create_by       BIGINT,
    update_by       BIGINT,
    deleted         INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_role_menu (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0,
    role_id         BIGINT NOT NULL,
    menu_id         BIGINT NOT NULL
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
    ticket_type_id  BIGINT,
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

ALTER TABLE wf_process_def ADD COLUMN IF NOT EXISTS ticket_type_id BIGINT;

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
    no_prefix       VARCHAR(64),
    no_date_pattern VARCHAR(32) DEFAULT 'yyyyMMdd',
    no_seq_len      INT DEFAULT 4,
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
    status          VARCHAR(16) DEFAULT 'DRAFT',
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
    status          VARCHAR(16) DEFAULT 'DRAFT',
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
    status          VARCHAR(16) DEFAULT 'DRAFT',
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
    schema_version  INT,
    form_data       JSONB,
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    create_by       BIGINT,
    update_by       BIGINT,
    deleted         INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tk_ticket_file (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0,
    ticket_id       BIGINT,
    file_name       VARCHAR(256) NOT NULL,
    content_type    VARCHAR(128),
    file_size       BIGINT,
    storage_path    VARCHAR(512) NOT NULL,
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    create_by       BIGINT,
    update_by       BIGINT,
    deleted         INT DEFAULT 0
);

ALTER TABLE tk_type ADD COLUMN IF NOT EXISTS no_prefix VARCHAR(64);
ALTER TABLE tk_type ADD COLUMN IF NOT EXISTS no_date_pattern VARCHAR(32) DEFAULT 'yyyyMMdd';
ALTER TABLE tk_type ADD COLUMN IF NOT EXISTS no_seq_len INT DEFAULT 4;
ALTER TABLE tk_form_ui ADD COLUMN IF NOT EXISTS status VARCHAR(16) DEFAULT 'PUBLISHED';
ALTER TABLE tk_list_ui ADD COLUMN IF NOT EXISTS status VARCHAR(16) DEFAULT 'PUBLISHED';
ALTER TABLE tk_detail_ui ADD COLUMN IF NOT EXISTS status VARCHAR(16) DEFAULT 'PUBLISHED';
ALTER TABLE tk_ticket ADD COLUMN IF NOT EXISTS schema_version INT;
UPDATE tk_form_ui SET status = 'PUBLISHED' WHERE status IS NULL OR status = '';
UPDATE tk_list_ui SET status = 'PUBLISHED' WHERE status IS NULL OR status = '';
UPDATE tk_detail_ui SET status = 'PUBLISHED' WHERE status IS NULL OR status = '';

-- 工单量随使用时间增长，日常入口都是这几条路径：
--   按类型分页、按类型+状态筛、按工单号精确查、按发起人做数据范围、按实例回写
CREATE INDEX IF NOT EXISTS idx_tk_ticket_type_time ON tk_ticket (type_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_tk_ticket_type_status_time ON tk_ticket (type_id, status, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_tk_ticket_no ON tk_ticket (ticket_no);
CREATE INDEX IF NOT EXISTS idx_tk_ticket_starter_time ON tk_ticket (starter_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_tk_ticket_proc_inst ON tk_ticket (process_inst_id);
CREATE INDEX IF NOT EXISTS idx_tk_ticket_file_ticket ON tk_ticket_file (ticket_id);
CREATE INDEX IF NOT EXISTS idx_tk_field_type ON tk_field (type_id);
CREATE INDEX IF NOT EXISTS idx_tk_form_ui_type ON tk_form_ui (type_id, status, version DESC);
CREATE INDEX IF NOT EXISTS idx_tk_list_ui_type ON tk_list_ui (type_id, status, version DESC);
CREATE INDEX IF NOT EXISTS idx_tk_detail_ui_type ON tk_detail_ui (type_id, status, version DESC);
CREATE INDEX IF NOT EXISTS idx_wf_inst_ext_business ON wf_process_instance_ext (business_key);
CREATE INDEX IF NOT EXISTS idx_wf_inst_ext_inst ON wf_process_instance_ext (process_inst_id);

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
