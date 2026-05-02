package com.xiaomi.sdk.controller;

import com.xiaomi.sdk.entity.FolderConfigEntity;
import com.xiaomi.sdk.exception.XiaomiAuthException;
import com.xiaomi.sdk.mapper.FolderConfigMapper;
import com.xiaomi.sdk.model.MusicResult;
import com.xiaomi.sdk.model.PlayerStatus;
import com.xiaomi.sdk.music.AutoPlayManager;
import com.xiaomi.sdk.music.MusicService;
import com.xiaomi.sdk.music.PlayerStatusScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 音乐控制控制器
 * @author awen
 */
@RestController
@RequestMapping("/api/music")
public class MusicController {

    private static final Logger log = LoggerFactory.getLogger(MusicController.class);

    private final MusicService musicService;
    private final PlayerStatusScheduler statusScheduler;
    private final FolderConfigMapper configMapper;

    public MusicController(MusicService musicService, PlayerStatusScheduler statusScheduler,
                           FolderConfigMapper configMapper) {
        this.musicService = musicService;
        this.statusScheduler = statusScheduler;
        this.configMapper = configMapper;
    }

    @PostMapping("/play")
    public ResponseEntity<Map<String, Object>> play(@RequestBody Map<String, Object> body) {
        String deviceId = (String) body.get("deviceId");
        String url = (String) body.get("url");
        boolean direct = Boolean.TRUE.equals(body.get("direct"));
        log.info("播放请求: deviceId={}, direct={}, url={}", deviceId, direct, url);
        MusicResult result = direct ? musicService.playDirect(deviceId, url) : musicService.play(deviceId, url);
        if (result.success()) {
            try { statusScheduler.forcePoll(); } catch (Exception e) { /* ignore */ }
        }
        return ResponseEntity.ok(Map.of(
                "success", result.success(), "data", "", "message", result.message()
        ));
    }

    @PostMapping("/pause")
    public ResponseEntity<Map<String, Object>> pause(@RequestBody Map<String, String> body) {
        String deviceId = body.get("deviceId");
        MusicResult result = musicService.pause(deviceId);
        if (result.success()) {
            statusScheduler.updateCachedStatus(2);
            statusScheduler.broadcastStatus();
        }
        return ResponseEntity.ok(Map.of(
                "success", result.success(), "data", "", "message", result.message()
        ));
    }

    @PostMapping("/next")
    public ResponseEntity<Map<String, Object>> next(@RequestBody Map<String, String> body) {
        String deviceId = body.get("deviceId");
        MusicResult result = musicService.next(deviceId);
        return ResponseEntity.ok(Map.of(
                "success", result.success(), "data", "", "message", result.message()
        ));
    }

    @PostMapping("/prev")
    public ResponseEntity<Map<String, Object>> prev(@RequestBody Map<String, String> body) {
        String deviceId = body.get("deviceId");
        MusicResult result = musicService.prev(deviceId);
        return ResponseEntity.ok(Map.of(
                "success", result.success(), "data", "", "message", result.message()
        ));
    }

    @PostMapping("/volume")
    public ResponseEntity<Map<String, Object>> volume(@RequestBody Map<String, Object> body) {
        String deviceId = (String) body.get("deviceId");
        int volume = ((Number) body.get("volume")).intValue();
        MusicResult result = musicService.setVolume(deviceId, volume);
        if (result.success()) {
            statusScheduler.updateCachedVolume(volume);
            statusScheduler.broadcastStatus();
        }
        return ResponseEntity.ok(Map.of(
                "success", result.success(), "data", "", "message", result.message()
        ));
    }

    @PostMapping("/resume")
    public ResponseEntity<Map<String, Object>> resume(@RequestBody Map<String, String> body) {
        String deviceId = body.get("deviceId");
        MusicResult result = musicService.resume(deviceId);
        if (result.success()) {
            statusScheduler.updateCachedStatus(1);
            statusScheduler.broadcastStatus();
        }
        return ResponseEntity.ok(Map.of(
                "success", result.success(), "data", "", "message", result.message()
        ));
    }

    @PostMapping("/mode")
    public ResponseEntity<Map<String, Object>> setMode(@RequestBody Map<String, Object> body) {
        int mode = ((Number) body.get("mode")).intValue();
        log.info("保存播放模式: mode={}", mode);
        savePlayMode(mode);
        return ResponseEntity.ok(Map.of("success", true, "data", "", "message", "ok"));
    }

    private void savePlayMode(int mode) {
        FolderConfigEntity entity = configMapper.selectListByQuery(
                com.mybatisflex.core.query.QueryWrapper.create().limit(1)
        ).stream().findFirst().orElse(null);
        if (entity == null) {
            entity = new FolderConfigEntity();
            entity.setId(1L);
            entity.setCreatedAt(OffsetDateTime.now());
        }
        entity.setPlayMode(mode);
        entity.setUpdatedAt(OffsetDateTime.now());
        if (entity.getId() != null && entity.getId() == 1L && configMapper.selectOneById(1L) == null) {
            configMapper.insert(entity);
        } else {
            configMapper.update(entity);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(@RequestParam String deviceId,
                                                       @RequestParam(required = false) String did,
                                                       @RequestParam(defaultValue = "false") boolean force) {
        try {
            PlayerStatus status = force ? statusScheduler.forcePoll() : statusScheduler.getCachedStatus();
            return ResponseEntity.ok(Map.of("success", true, "data", status, "message", "ok"));
        } catch (XiaomiAuthException e) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false, "data", "", "message", "未登录或登录已过期"
            ));
        }
    }

    @GetMapping(value = "/status/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter statusStream(@RequestParam String deviceId) {
        statusScheduler.start(deviceId, statusScheduler.getIntervalSeconds());
        return statusScheduler.createEmitter();
    }

    @PostMapping("/autoplay/disable")
    public ResponseEntity<Map<String, Object>> disableAutoPlay() {
        AutoPlayManager autoPlay = statusScheduler.getAutoPlayManager();
        if (autoPlay != null) {
            autoPlay.disable();
        }
        return ResponseEntity.ok(Map.of("success", true, "data", "", "message", "ok"));
    }

    @PostMapping("/status/interval")
    public ResponseEntity<Map<String, Object>> setInterval(@RequestBody Map<String, Object> body) {
        String deviceId = (String) body.get("deviceId");
        int interval = ((Number) body.get("interval")).intValue();
        if (interval < 1) interval = 1;
        if (interval > 20) interval = 20;
        log.info("前端设置定时刷新间隔: {}秒, deviceId={}", interval, deviceId);
        statusScheduler.start(deviceId, interval);
        return ResponseEntity.ok(Map.of("success", true, "message", "ok"));
    }
}
