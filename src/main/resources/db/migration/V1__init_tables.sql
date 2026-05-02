-- ============================================================
-- 小米音箱控制 SDK 初始化表结构
-- @author awen
-- ============================================================

-- 账号与 Token 表
CREATE TABLE xm_account (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         VARCHAR(64)     NOT NULL UNIQUE,
    pass_token      TEXT            NOT NULL,
    ssecurity       TEXT            NOT NULL,
    service_token   TEXT            NOT NULL,
    service_token_expire BIGINT     NOT NULL DEFAULT 0,
    api_token       VARCHAR(256)    NULL,
    api_token_expire    BIGINT     NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE  xm_account              IS '小米账号与 Token';
COMMENT ON COLUMN xm_account.user_id              IS '小米用户 ID';
COMMENT ON COLUMN xm_account.pass_token           IS 'passToken（AES-256 加密存储）';
COMMENT ON COLUMN xm_account.ssecurity            IS 'ssecurity（AES-256 加密存储）';
COMMENT ON COLUMN xm_account.service_token        IS 'serviceToken（AES-256 加密存储）';
COMMENT ON COLUMN xm_account.service_token_expire IS 'serviceToken 过期时间（毫秒时间戳）';
COMMENT ON COLUMN xm_account.api_token            IS '内部 API Token（JWT）';
COMMENT ON COLUMN xm_account.api_token_expire     IS 'API Token 过期时间（毫秒时间戳）';

-- 设备表
CREATE TABLE xm_device (
    id              BIGSERIAL       PRIMARY KEY,
    did             VARCHAR(64)     NOT NULL,
    name            VARCHAR(128)    NOT NULL DEFAULT '',
    model           VARCHAR(128)    NOT NULL DEFAULT '',
    token           VARCHAR(256)    NOT NULL DEFAULT '',
    device_id       VARCHAR(64)     NOT NULL DEFAULT '',
    hardware        VARCHAR(128)    NOT NULL DEFAULT '',
    account_user_id VARCHAR(64)     NOT NULL REFERENCES xm_account(user_id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE  xm_device                IS '小米设备';
COMMENT ON COLUMN xm_device.did                IS 'MIoT 设备 ID';
COMMENT ON COLUMN xm_device.name               IS '设备别名';
COMMENT ON COLUMN xm_device.model              IS '设备型号';
COMMENT ON COLUMN xm_device.token              IS '设备 Token';
COMMENT ON COLUMN xm_device.device_id          IS 'MiNA 设备 ID';
COMMENT ON COLUMN xm_device.hardware           IS '硬件版本';
COMMENT ON COLUMN xm_device.account_user_id    IS '关联的小米用户 ID';

CREATE INDEX idx_xm_device_account_user_id ON xm_device(account_user_id);
CREATE UNIQUE INDEX idx_xm_device_did_user ON xm_device(did, account_user_id);

-- Token 变更审计表
CREATE TABLE xm_token_history (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         VARCHAR(64)     NOT NULL,
    action          VARCHAR(32)     NOT NULL,
    ip_address      VARCHAR(45)     NULL,
    user_agent      VARCHAR(512)    NULL,
    detail          TEXT            NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE  xm_token_history         IS 'Token 变更审计日志';
COMMENT ON COLUMN xm_token_history.user_id      IS '小米用户 ID';
COMMENT ON COLUMN xm_token_history.action       IS '操作类型：LOGIN / REFRESH / LOGOUT';
COMMENT ON COLUMN xm_token_history.ip_address   IS '客户端 IP';
COMMENT ON COLUMN xm_token_history.user_agent   IS '客户端 User-Agent';
COMMENT ON COLUMN xm_token_history.detail       IS '操作详情';

CREATE INDEX idx_xm_token_history_user_id ON xm_token_history(user_id);
CREATE INDEX idx_xm_token_history_created_at ON xm_token_history(created_at);
