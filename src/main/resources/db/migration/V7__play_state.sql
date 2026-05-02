-- 单记录播放状态表，替代无限增长的 xm_play_history
CREATE TABLE xm_play_state (
    id          BIGINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    folder_path TEXT NOT NULL DEFAULT '',
    file_name   TEXT NOT NULL DEFAULT '',
    file_path   TEXT NOT NULL DEFAULT '',
    url_path    TEXT NOT NULL DEFAULT '',
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  xm_play_state             IS '当前播放状态（单记录）';
COMMENT ON COLUMN xm_play_state.folder_path IS '文件夹路径';
COMMENT ON COLUMN xm_play_state.file_name   IS '音频文件名';
COMMENT ON COLUMN xm_play_state.file_path   IS '文件完整路径';
COMMENT ON COLUMN xm_play_state.url_path    IS '相对 URL 路径';
COMMENT ON COLUMN xm_play_state.updated_at  IS '最后更新时间';

-- 迁移旧数据最后一条
INSERT INTO xm_play_state (folder_path, file_name, file_path, url_path, updated_at)
SELECT folder_path, file_name, file_path, url_path, created_at
FROM xm_play_history ORDER BY created_at DESC LIMIT 1;

DROP TABLE xm_play_history;
