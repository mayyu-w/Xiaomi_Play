-- ============================================================
-- 重命名 api_token 列为 io_service_token，使命名与实际用途一致
-- @author awen
-- ============================================================

ALTER TABLE xm_account RENAME COLUMN api_token TO io_service_token;
ALTER TABLE xm_account RENAME COLUMN api_token_expire TO io_service_token_expire;

COMMENT ON COLUMN xm_account.io_service_token         IS 'xiaomiio serviceToken（AES-256 加密存储）';
COMMENT ON COLUMN xm_account.io_service_token_expire  IS 'xiaomiio serviceToken 过期时间（毫秒时间戳）';
