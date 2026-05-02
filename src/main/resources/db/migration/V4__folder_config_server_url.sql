ALTER TABLE xm_folder_config ADD COLUMN server_url TEXT NOT NULL DEFAULT '';
COMMENT ON COLUMN xm_folder_config.server_url IS '服务地址（音箱可访问的 URL，如 http://192.168.1.100:8080）';
