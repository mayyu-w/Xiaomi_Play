-- 定时任务表
CREATE TABLE xm_scheduled_task (
    id          BIGSERIAL       PRIMARY KEY,
    task_name   VARCHAR(128)    NOT NULL,
    device_id   VARCHAR(64)     NOT NULL,
    cron_expr   VARCHAR(64)     NOT NULL,
    command     VARCHAR(64)     NOT NULL,
    params      TEXT            NULL,
    enabled     BOOLEAN         NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE  xm_scheduled_task          IS '定时任务';
COMMENT ON COLUMN xm_scheduled_task.task_name IS '任务名称';
COMMENT ON COLUMN xm_scheduled_task.device_id IS 'MiNA device ID';
COMMENT ON COLUMN xm_scheduled_task.cron_expr IS 'Cron 表达式（秒 分 时 日 月 周）';
COMMENT ON COLUMN xm_scheduled_task.command   IS '执行命令';
COMMENT ON COLUMN xm_scheduled_task.params    IS '命令参数（JSON）';
COMMENT ON COLUMN xm_scheduled_task.enabled   IS '是否启用';

CREATE INDEX idx_xm_scheduled_task_enabled ON xm_scheduled_task(enabled);

-- 定时任务执行日志
CREATE TABLE xm_scheduled_task_log (
    id          BIGSERIAL       PRIMARY KEY,
    task_id     BIGINT          NOT NULL,
    task_name   VARCHAR(128)    NULL,
    device_id   VARCHAR(64)     NOT NULL,
    command     VARCHAR(64)     NOT NULL,
    success     BOOLEAN         NOT NULL DEFAULT false,
    message     TEXT            NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE  xm_scheduled_task_log IS '定时任务执行日志';

CREATE INDEX idx_xm_scheduled_task_log_task ON xm_scheduled_task_log(task_id);
CREATE INDEX idx_xm_scheduled_task_log_created ON xm_scheduled_task_log(created_at);

-- 语音命令配置（持久化轮询设置等）
CREATE TABLE xm_voice_config (
    id          BIGSERIAL       PRIMARY KEY,
    config_key  VARCHAR(64)     NOT NULL UNIQUE,
    config_value TEXT           NOT NULL,
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE xm_voice_config IS '语音命令配置';
