CREATE TABLE xm_play_history (
    id BIGSERIAL PRIMARY KEY,
    folder_path TEXT NOT NULL DEFAULT '',
    file_name TEXT NOT NULL DEFAULT '',
    file_path TEXT NOT NULL DEFAULT '',
    url_path TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE xm_play_history IS '播放历史记录';
COMMENT ON COLUMN xm_play_history.folder_path IS '文件夹路径';
COMMENT ON COLUMN xm_play_history.file_name IS '音频文件名';
COMMENT ON COLUMN xm_play_history.file_path IS '文件完整路径';
COMMENT ON COLUMN xm_play_history.url_path IS '相对 URL 路径';
