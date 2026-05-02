-- 语音命令执行日志表
CREATE TABLE xm_voice_command_log (
    id              BIGSERIAL       PRIMARY KEY,
    device_id       VARCHAR(64)     NOT NULL,
    query           TEXT            NOT NULL,
    matched_keyword VARCHAR(128)    NULL,
    command         VARCHAR(64)     NOT NULL,
    success         BOOLEAN         NOT NULL DEFAULT false,
    message         TEXT            NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE  xm_voice_command_log            IS '语音命令执行日志';
COMMENT ON COLUMN xm_voice_command_log.device_id   IS 'MiNA device ID';
COMMENT ON COLUMN xm_voice_command_log.query       IS '原始语音查询文本';
COMMENT ON COLUMN xm_voice_command_log.matched_keyword IS '匹配到的关键词';
COMMENT ON COLUMN xm_voice_command_log.command     IS '执行的命令动作';
COMMENT ON COLUMN xm_voice_command_log.success     IS '执行是否成功';

CREATE INDEX idx_xm_voice_command_log_device ON xm_voice_command_log(device_id);
CREATE INDEX idx_xm_voice_command_log_created ON xm_voice_command_log(created_at);
