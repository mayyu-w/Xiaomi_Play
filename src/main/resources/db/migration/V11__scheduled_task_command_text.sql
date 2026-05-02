-- command 列改为 TEXT，支持多命令 JSON 数组
ALTER TABLE xm_scheduled_task ALTER COLUMN command TYPE TEXT;
