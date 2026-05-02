package com.xiaomi.sdk.token;

import com.xiaomi.sdk.account.MiAccountService;
import com.xiaomi.sdk.model.LoginResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;

/**
 * Token 定时刷新调度器
 * 启动后 30 秒执行首次检测，之后每小时检测一次
 * 过期前 24 小时主动续期；启动时已过期则日志提示重新登录
 * @author awen
 */
public class TokenRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(TokenRefreshScheduler.class);

    private final MiAccountService accountService;
    private final TokenManager tokenManager;

    private volatile boolean startupCheckDone = false;

    public TokenRefreshScheduler(MiAccountService accountService, TokenManager tokenManager) {
        this.accountService = accountService;
        this.tokenManager = tokenManager;
    }

    @Scheduled(initialDelay = 30_000, fixedRate = 3_600_000)
    public void refreshIfNeeded() {
        try {
            LoginResult token = tokenManager.loadActiveToken();
            if (token == null) {
                if (!startupCheckDone) {
                    log.info("启动检测：无活跃 Token，等待用户登录");
                    startupCheckDone = true;
                }
                return;
            }

            long now = System.currentTimeMillis();
            long expireAt = token.serviceTokenExpire();
            long remainMs = expireAt - now;
            long remainHours = remainMs / (60 * 60 * 1000);

            if (expireAt <= now) {
                log.warn("启动检测：Token 已过期, userId={}, 过期于 {}, 请前往首页重新登录",
                        token.userId(), Instant.ofEpochMilli(expireAt));
                startupCheckDone = true;
                return;
            }

            if (!startupCheckDone) {
                log.info("启动检测：Token 有效, userId={}, 剩余 {} 小时",
                        token.userId(), remainHours);
                startupCheckDone = true;
            }

            if (tokenManager.needsProactiveRefresh(token)) {
                log.info("Token 将在 {} 小时后过期，开始刷新, userId={}", remainHours, token.userId());
                LoginResult refreshed = accountService.refreshToken();
                log.info("Token 刷新成功, userId={}, 新过期时间={}",
                        refreshed.userId(), refreshed.serviceTokenExpire());
            }
        } catch (Exception e) {
            log.error("Token 定时刷新失败", e);
            startupCheckDone = true;
        }
    }
}
