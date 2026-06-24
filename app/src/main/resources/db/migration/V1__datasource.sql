-- data-foundation 数据源登记表（M2 WP1 · 锚点链持久层）。
-- 多租户（D9）：每行带 tenant_id，写落、读按 X-Tenant-Id 强制过滤；(tenant_id, name) 租户内唯一。
-- 凭据红线（D7）：口令仅以 AES-GCM 密文存 secret_cipher，明文绝不落库；secret_ref 为 ESO/Vault 间接层预留（M2 留空）。
-- 连接器中立（D8）：type 为方言类型键（mysql/postgresql/...），端点据此选方言、拼 URL，不写死 MySQL。
-- 占位/示例一律脱敏（acme / tenant-demo / example.com）——红线见 CLAUDE.md。

CREATE TABLE data_source (
    id              UUID         PRIMARY KEY,
    -- 租户路由键 = 数据隔离边界，对齐 ICD tenant-context-headers 的 X-Tenant-Id。
    tenant_id       VARCHAR(63)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    -- 方言类型键（mysql / postgresql / ...）；新增方言 = 加插件，不改本表。
    type            VARCHAR(32)  NOT NULL,
    host            VARCHAR(255) NOT NULL,
    port            INTEGER      NOT NULL,
    -- 目标库名（"database" 在 PG 为保留字，列名用 db_name）。
    db_name         VARCHAR(255) NOT NULL,
    username        VARCHAR(255) NOT NULL,
    -- 口令 AES-256-GCM 密文（Base64）：iv(12B) || ciphertext+tag，主密钥部署期注入（不入库）。
    secret_cipher   VARCHAR(4096) NOT NULL,
    -- 凭据间接引用预留列（ESO/Vault 逻辑名）：M2 留空，后续切换不改表。
    secret_ref      VARCHAR(512),

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- 乐观锁，防并发更新脏写。
    version         BIGINT       NOT NULL DEFAULT 0,

    -- 租户内同名唯一（跨租户可同名，互不可见）。
    CONSTRAINT uq_data_source_tenant_name UNIQUE (tenant_id, name)
);

-- 按租户列表是主查询路径（D9），建索引。
CREATE INDEX idx_data_source_tenant ON data_source (tenant_id);
