package com.xiaomi.sdk.token;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.xiaomi.sdk.crypto.CryptoService;
import com.xiaomi.sdk.entity.AccountEntity;
import com.xiaomi.sdk.entity.TokenHistoryEntity;
import com.xiaomi.sdk.mapper.AccountMapper;
import com.xiaomi.sdk.mapper.TokenHistoryMapper;
import com.xiaomi.sdk.model.LoginResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static com.xiaomi.sdk.entity.table.AccountEntityTableDef.ACCOUNT_ENTITY;

/**
 * Token 管理器：Redis 缓存 + PostgreSQL 持久化
 * @author awen
 */
public class TokenManager {

    private static final String REDIS_ACTIVE_USER = "xm:active_user";
    private static final String REDIS_TOKEN_PREFIX = "xm:token:";
    static final long REFRESH_BUFFER_MS = 5 * 60 * 1000;
    static final long PROACTIVE_REFRESH_MS = 24 * 60 * 60 * 1000;

    /** 后台线程审计上下文（用于无 HTTP 请求时传递 IP/UA） */
    private static final ThreadLocal<String[]> auditContext = new ThreadLocal<>();

    public static void setAuditContext(String ip, String userAgent) {
        auditContext.set(new String[]{ip, userAgent});
    }

    public static void clearAuditContext() {
        auditContext.remove();
    }

    private final StringRedisTemplate redisTemplate;
    private final AccountMapper accountMapper;
    private final TokenHistoryMapper tokenHistoryMapper;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;

    public TokenManager(StringRedisTemplate redisTemplate,
                        AccountMapper accountMapper,
                        TokenHistoryMapper tokenHistoryMapper,
                        CryptoService cryptoService,
                        ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.accountMapper = accountMapper;
        this.tokenHistoryMapper = tokenHistoryMapper;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存 Token 到 Redis + DB，并记录审计日志
     */
    public void saveToken(LoginResult token, String action, String detail) {
        String userId = token.userId();

        // 加密敏感字段
        String encPassToken = encrypt(token.passToken());
        String encSsecurity = encrypt(token.ssecurity());
        String encServiceToken = encrypt(token.serviceToken());

        // 保存到 Redis
        Map<String, String> fields = new HashMap<>();
        fields.put("userId", userId);
        fields.put("passToken", encPassToken);
        fields.put("ssecurity", encSsecurity);
        fields.put("serviceToken", encServiceToken);
        fields.put("serviceTokenExpire", String.valueOf(token.serviceTokenExpire()));
        String encIoServiceToken = encrypt(token.ioServiceToken());

        fields.put("ioServiceToken", encIoServiceToken != null ? encIoServiceToken : "");
        fields.put("ioServiceTokenExpire", String.valueOf(token.ioServiceTokenExpire()));

        try {
            String json = objectMapper.writeValueAsString(fields);
            long ttlSec = (token.serviceTokenExpire() - System.currentTimeMillis()) / 1000;
            if (ttlSec > 0) {
                redisTemplate.opsForValue().set(REDIS_TOKEN_PREFIX + userId, json,
                        Duration.ofSeconds(ttlSec));
            } else {
                redisTemplate.opsForValue().set(REDIS_TOKEN_PREFIX + userId, json);
            }
            redisTemplate.opsForValue().set(REDIS_ACTIVE_USER, userId);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Token 序列化失败", e);
        }

        // Upsert 到 DB
        AccountEntity existing = accountMapper.selectOneByQuery(
                QueryWrapper.create().where(ACCOUNT_ENTITY.USER_ID.eq(userId)));

        AccountEntity entity = new AccountEntity();
        entity.setUserId(userId);
        entity.setPassToken(encPassToken);
        entity.setSsecurity(encSsecurity);
        entity.setServiceToken(encServiceToken);
        entity.setServiceTokenExpire(token.serviceTokenExpire());
        entity.setIoServiceToken(encrypt(token.ioServiceToken()));
        entity.setIoServiceTokenExpire(token.ioServiceTokenExpire());
        entity.setUpdatedAt(OffsetDateTime.now());

        if (existing != null) {
            entity.setId(existing.getId());
            entity.setCreatedAt(existing.getCreatedAt());
            accountMapper.update(entity);
        } else {
            entity.setCreatedAt(OffsetDateTime.now());
            accountMapper.insert(entity);
        }

        // 审计日志
        recordHistory(userId, action, detail);
    }

    /**
     * 加载活跃用户的 Token：Redis → DB → null
     */
    public LoginResult loadActiveToken() {
        String userId = redisTemplate.opsForValue().get(REDIS_ACTIVE_USER);
        if (userId == null) {
            // 从 DB 加载最近更新的账号
            AccountEntity latest = accountMapper.selectOneByQuery(
                    QueryWrapper.create().orderBy(ACCOUNT_ENTITY.UPDATED_AT, false).limit(1));
            if (latest == null) return null;
            userId = latest.getUserId();
            // 回填 Redis 活跃用户
            redisTemplate.opsForValue().set(REDIS_ACTIVE_USER, userId);
        }
        return loadToken(userId);
    }

    /**
     * 按 userId 加载 Token：Redis → DB → null
     */
    public LoginResult loadToken(String userId) {
        // 先查 Redis
        String json = redisTemplate.opsForValue().get(REDIS_TOKEN_PREFIX + userId);
        if (json != null) {
            LoginResult token = deserializeToken(json);
            if (token != null) return token;
        }

        // 查 DB
        AccountEntity entity = accountMapper.selectOneByQuery(
                QueryWrapper.create().where(ACCOUNT_ENTITY.USER_ID.eq(userId)));
        if (entity == null) return null;

        LoginResult token = new LoginResult(
                entity.getUserId(),
                decrypt(entity.getPassToken()),
                decrypt(entity.getSsecurity()),
                decrypt(entity.getServiceToken()),
                entity.getServiceTokenExpire(),
                decrypt(entity.getIoServiceToken()),
                entity.getIoServiceTokenExpire()
        );

        // 回填 Redis
        saveToRedis(token);
        return token;
    }

    /**
     * 删除 Token
     */
    public void removeToken(String userId, String detail) {
        if (userId != null) {
            redisTemplate.delete(REDIS_TOKEN_PREFIX + userId);
            accountMapper.deleteByQuery(
                    QueryWrapper.create().where(ACCOUNT_ENTITY.USER_ID.eq(userId)));
        }
        redisTemplate.delete(REDIS_ACTIVE_USER);
        recordHistory(userId, "LOGOUT", detail);
    }

    /**
     * 检查 Token 是否即将过期（5 分钟内）
     */
    public boolean isExpiringSoon(LoginResult token) {
        return token != null &&
                token.serviceTokenExpire() - System.currentTimeMillis() < REFRESH_BUFFER_MS;
    }

    /**
     * 检查 Token 是否需要主动刷新（24 小时内过期）
     */
    public boolean needsProactiveRefresh(LoginResult token) {
        return token != null &&
                token.serviceTokenExpire() - System.currentTimeMillis() < PROACTIVE_REFRESH_MS;
    }

    // ---- 内部方法 ----

    private void saveToRedis(LoginResult token) {
        Map<String, String> fields = new HashMap<>();
        fields.put("userId", token.userId());
        fields.put("passToken", encrypt(token.passToken()));
        fields.put("ssecurity", encrypt(token.ssecurity()));
        fields.put("serviceToken", encrypt(token.serviceToken()));
        fields.put("serviceTokenExpire", String.valueOf(token.serviceTokenExpire()));
        fields.put("ioServiceToken", token.ioServiceToken() != null ? token.ioServiceToken() : "");
        fields.put("ioServiceTokenExpire", String.valueOf(token.ioServiceTokenExpire()));

        try {
            String json = objectMapper.writeValueAsString(fields);
            long ttlSec = (token.serviceTokenExpire() - System.currentTimeMillis()) / 1000;
            if (ttlSec > 0) {
                redisTemplate.opsForValue().set(REDIS_TOKEN_PREFIX + token.userId(), json,
                        Duration.ofSeconds(ttlSec));
            }
        } catch (JsonProcessingException ignored) {
        }
    }

    private LoginResult deserializeToken(String json) {
        try {
            Map<String, String> fields = objectMapper.readValue(json, Map.class);
            return new LoginResult(
                    fields.get("userId"),
                    decrypt(fields.get("passToken")),
                    decrypt(fields.get("ssecurity")),
                    decrypt(fields.get("serviceToken")),
                    Long.parseLong(fields.get("serviceTokenExpire")),
                    decrypt(fields.get("ioServiceToken")),
                    Long.parseLong(fields.get("ioServiceTokenExpire"))
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String encrypt(String plaintext) {
        if (plaintext == null || !cryptoService.hasAesKey()) return plaintext;
        return cryptoService.encrypt(plaintext);
    }

    private String decrypt(String ciphertext) {
        if (ciphertext == null || !cryptoService.hasAesKey()) return ciphertext;
        return cryptoService.decrypt(ciphertext);
    }

    private void recordHistory(String userId, String action, String detail) {
        String ip = null;
        String userAgent = null;

        // 优先使用 ThreadLocal 审计上下文（后台线程设置）
        String[] ctx = auditContext.get();
        if (ctx != null) {
            ip = ctx[0];
            userAgent = ctx[1];
        }

        // 回退到 HTTP 请求上下文
        if (ip == null) {
            try {
                ServletRequestAttributes attrs =
                        (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
                HttpServletRequest request = attrs.getRequest();
                ip = request.getRemoteAddr();
                userAgent = request.getHeader("User-Agent");
            } catch (Exception ignored) {
            }
        }

        TokenHistoryEntity history = new TokenHistoryEntity();
        history.setUserId(userId);
        history.setAction(action);
        history.setIpAddress(ip);
        history.setUserAgent(userAgent);
        history.setDetail(detail);
        history.setCreatedAt(OffsetDateTime.now());
        tokenHistoryMapper.insert(history);
    }
}
