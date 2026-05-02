package com.xiaomi.sdk.music;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomi.sdk.account.MiAccountService;
import com.xiaomi.sdk.exception.XiaomiApiException;
import com.xiaomi.sdk.exception.XiaomiAuthException;
import com.xiaomi.sdk.model.PlayerStatus;
import com.xiaomi.sdk.mina.MiNAService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.xiaomi.sdk.mapper.FolderConfigMapper;
import com.xiaomi.sdk.mapper.PlayStateMapper;

/**
 * 播放状态定时轮询调度器 + SSE 广播
 * @author awen
 */
public class PlayerStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(PlayerStatusScheduler.class);

    private final MiNAService minaService;
    private final MiAccountService accountService;
    private final ObjectMapper objectMapper;
    private final AutoPlayManager autoPlayManager;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private volatile ScheduledFuture<?> task;
    private volatile PlayerStatus cachedStatus = new PlayerStatus(0, 0, "", "", 0, 0, 0);
    private volatile String deviceId;
    private volatile int intervalSeconds = 1;
    private static final int PLAYING_INTERVAL = 1;
    private static final int IDLE_INTERVAL = 5;
    private volatile boolean tokenExpired = false;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public PlayerStatusScheduler(MiNAService minaService, MiAccountService accountService,
                                 ObjectMapper objectMapper, AutoPlayManager autoPlayManager) {
        this.minaService = minaService;
        this.accountService = accountService;
        this.objectMapper = objectMapper;
        this.autoPlayManager = autoPlayManager;
    }

    public synchronized void start(String deviceId, int intervalSeconds) {
        this.deviceId = deviceId;
        this.intervalSeconds = intervalSeconds;
        this.tokenExpired = false;
        log.info("播放状态定时任务启动/更新: deviceId={}, 间隔={}秒", deviceId, intervalSeconds);
        reschedule();
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
            log.info("播放状态定时任务已停止");
        }
    }

    public PlayerStatus getCachedStatus() {
        if (tokenExpired) {
            throw new XiaomiAuthException("AUTH_006", "Token 已过期，请重新登录");
        }
        return cachedStatus;
    }

    public void updateCachedVolume(int volume) {
        PlayerStatus s = cachedStatus;
        cachedStatus = new PlayerStatus(s.status(), volume, s.mediaType(),
                s.mediaId(), s.playTime(), s.duration(), s.loopType());
    }

    public void updateCachedLoopType(int loopType) {
        PlayerStatus s = cachedStatus;
        cachedStatus = new PlayerStatus(s.status(), s.volume(), s.mediaType(),
                s.mediaId(), s.playTime(), s.duration(), loopType);
    }

    public void updateCachedStatus(int status) {
        PlayerStatus s = cachedStatus;
        cachedStatus = new PlayerStatus(status, s.volume(), s.mediaType(),
                s.mediaId(), s.playTime(), s.duration(), s.loopType());
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public AutoPlayManager getAutoPlayManager() {
        return autoPlayManager;
    }

    // ---- SSE ----

    private Map<String, Object> ssePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", System.currentTimeMillis());
        payload.put("playerStatus", cachedStatus);
        if (autoPlayManager != null) {
            payload.put("autoPlay", autoPlayManager.isEnabled());
            payload.put("currentUrlPath", autoPlayManager.getCurrentUrlPath());
        }
        return payload;
    }

    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("status").data(ssePayload()));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public void broadcastStatus() {
        List<SseEmitter> dead = new ArrayList<>();
        Map<String, Object> payload = ssePayload();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("status").data(payload));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }

    // ---- 调度 ----

    private void adjustInterval() {
        int target = (cachedStatus.status() == 1) ? PLAYING_INTERVAL : IDLE_INTERVAL;
        if (target != intervalSeconds) {
            intervalSeconds = target;
            log.debug("动态调整轮询间隔: {}秒 (status={})", target, cachedStatus.status());
            reschedule();
        }
    }

    private void reschedule() {
        if (task != null) {
            task.cancel(false);
        }
        if (deviceId == null || deviceId.isEmpty()) return;
        task = executor.scheduleAtFixedRate(this::pollStatus, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    public PlayerStatus forcePoll() {
        if (tokenExpired) {
            throw new XiaomiAuthException("AUTH_006", "Token 已过期，请重新登录");
        }
        pollStatus();
        if (tokenExpired) {
            throw new XiaomiAuthException("AUTH_006", "Token 已过期，请重新登录");
        }
        return cachedStatus;
    }

    private void pollStatus() {
        try {
            var result = minaService.playerGetStatus(deviceId);
            if (result.success() && result.data() != null) {
                String infoStr = result.data().path("info").asText("");
                if (!infoStr.isEmpty()) {
                    JsonNode info = objectMapper.readTree(infoStr);
                    JsonNode detail = info.path("play_song_detail");
                    long playTime = detail.path("position").asLong(0) / 1000;
                    long duration = detail.path("duration").asLong(0) / 1000;
                    PlayerStatus prevStatus = cachedStatus;
                    cachedStatus = new PlayerStatus(
                            info.path("status").asInt(0),
                            info.path("volume").asInt(0),
                            info.path("media_type").asText(""),
                            detail.path("audio_id").asText(""),
                            playTime,
                            duration,
                            info.path("loop_type").asInt(0)
                    );
                    // 自动切歌检测
                    if (autoPlayManager != null && autoPlayManager.isEnabled()) {
                        autoPlayManager.checkAndNext(prevStatus, cachedStatus);
                    }
                    adjustInterval();
                    broadcastStatus();
                }
            }
        } catch (XiaomiAuthException e) {
            handleTokenExpired();
        } catch (XiaomiApiException e) {
            if (e.getHttpStatus() == 401) {
                handleTokenExpired();
            } else {
                log.warn("定时获取播放状态失败: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("定时获取播放状态失败: {}", e.getMessage());
        }
    }

    private void handleTokenExpired() {
        log.warn("检测到 401，尝试自动刷新 Token");
        try {
            accountService.refreshToken();
            tokenExpired = false;
            log.info("Token 自动刷新成功，恢复轮询");
        } catch (Exception ex) {
            tokenExpired = true;
            log.error("Token 自动刷新失败，定时任务已暂停: {}", ex.getMessage());
            stop();
        }
    }

    @PreDestroy
    public void destroy() {
        for (SseEmitter emitter : emitters) {
            emitter.complete();
        }
        emitters.clear();
        if (task != null) task.cancel(false);
        executor.shutdownNow();
    }
}
