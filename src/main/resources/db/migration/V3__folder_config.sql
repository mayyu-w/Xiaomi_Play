-- 文件夹监控配置表（单行，仅一条记录）
CREATE TABLE xm_folder_config (
    id              BIGSERIAL       PRIMARY KEY,
    path            TEXT            NOT NULL DEFAULT '',
    watch_enabled   BOOLEAN         NOT NULL DEFAULT false,
    watch_interval  INT             NOT NULL DEFAULT 10,
    ignore_dirs     TEXT            NOT NULL DEFAULT '',
    max_depth       INT             NOT NULL DEFAULT 10,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE  xm_folder_config             IS '文件夹监控配置';
COMMENT ON COLUMN xm_folder_config.path         IS '音频文件夹根路径';
COMMENT ON COLUMN xm_folder_config.watch_enabled IS '文件监控开关';
COMMENT ON COLUMN xm_folder_config.watch_interval IS '监控间隔（秒）';
COMMENT ON COLUMN xm_folder_config.ignore_dirs  IS '忽略的目录名，逗号分隔';
COMMENT ON COLUMN xm_folder_config.max_depth    IS '目录扫描深度';
