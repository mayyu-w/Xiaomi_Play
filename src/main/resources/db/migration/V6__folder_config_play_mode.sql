-- 播放模式配置（前端应用侧管理，非设备状态）
ALTER TABLE xm_folder_config ADD COLUMN play_mode INT DEFAULT 4;
COMMENT ON COLUMN xm_folder_config.play_mode IS '播放模式：0单曲循环 1全部循环 2随机 3单曲播放 4顺序播放';
