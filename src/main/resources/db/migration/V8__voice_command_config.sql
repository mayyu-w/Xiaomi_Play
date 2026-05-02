-- 语音命令关键词配置表
CREATE TABLE xm_voice_command (
    id          BIGSERIAL       PRIMARY KEY,
    keyword     VARCHAR(128)    NOT NULL,
    command     VARCHAR(64)     NOT NULL,
    enabled     BOOLEAN         NOT NULL DEFAULT true,
    sort_order  INT             NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE  xm_voice_command          IS '语音命令关键词配置';
COMMENT ON COLUMN xm_voice_command.keyword   IS '匹配关键词';
COMMENT ON COLUMN xm_voice_command.command   IS '命令动作：内置动作名或 exec# 自定义文本';
COMMENT ON COLUMN xm_voice_command.enabled   IS '是否启用';
COMMENT ON COLUMN xm_voice_command.sort_order IS '优先级（越小越高）';

CREATE INDEX idx_xm_voice_command_enabled ON xm_voice_command(enabled);
