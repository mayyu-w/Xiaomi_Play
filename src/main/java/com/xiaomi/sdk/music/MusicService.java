package com.xiaomi.sdk.music;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiaomi.sdk.mina.MiNAService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.xiaomi.sdk.model.MusicResult;
import com.xiaomi.sdk.model.PlayerStatus;

import java.util.Map;

/**
 * 音乐播放控制服务（源自 xiaomusic）
 * 基于 MiNAService 的 ubus 协议实现
 * @author awen
 */
public class MusicService {

    private static final Logger log = LoggerFactory.getLogger(MusicService.class);

    private final MiNAService minaService;

    public MusicService(MiNAService minaService) {
        this.minaService = minaService;
    }

    /**
     * 播放
     */
    public MusicResult play(String deviceId, String url) {
        minaService.playerPause(deviceId);
        minaService.playerStop(deviceId);
        boolean success = minaService.playByUrl(deviceId, url);
        return success ? MusicResult.ok() : MusicResult.fail("Play failed");
    }

    /**
     * 直接播放（跳过 pause/stop，用于自动切歌场景）
     */
    public MusicResult playDirect(String deviceId, String url) {
        boolean success = minaService.playByUrl(deviceId, url);
        return success ? MusicResult.ok() : MusicResult.fail("Play failed");
    }

    /**
     * 播放（带 audioId）
     */
    public MusicResult play(String deviceId, String url, String audioId) {
        boolean success = minaService.playByMusicUrl(deviceId, url, 0, audioId);
        return success ? MusicResult.ok() : MusicResult.fail("Play failed");
    }

    /**
     * 暂停
     */
    public MusicResult pause(String deviceId) {
        boolean success = minaService.playerPause(deviceId);
        return success ? MusicResult.ok() : MusicResult.fail("Pause failed");
    }

    /**
     * 恢复播放
     */
    public MusicResult resume(String deviceId) {
        boolean success = minaService.playerPlay(deviceId);
        return success ? MusicResult.ok() : MusicResult.fail("Resume failed");
    }

    /**
     * 下一首 — 先停止当前播放，调用方负责推送新 URL
     */
    public MusicResult next(String deviceId) {
        minaService.playerStop(deviceId);
        return MusicResult.ok();
    }

    /**
     * 上一首 — 先停止当前播放，调用方负责推送新 URL
     */
    public MusicResult prev(String deviceId) {
        minaService.playerStop(deviceId);
        return MusicResult.ok();
    }

    /**
     * 设置音量
     */
    public MusicResult setVolume(String deviceId, int volume) {
        if (volume < 0 || volume > 100) {
            return MusicResult.fail("Volume must be between 0 and 100");
        }
        boolean success = minaService.playerSetVolume(deviceId, volume);
        return success ? MusicResult.ok() : MusicResult.fail("Set volume failed");
    }

    /**
     * 设置播放模式
     */
    public MusicResult setPlayMode(String deviceId, int mode) {
        boolean success = minaService.playerSetLoop(deviceId, mode);
        return success ? MusicResult.ok() : MusicResult.fail("Set play mode failed");
    }

    /**
     * 获取播放器状态（通过 ubus player_get_play_status）
     */
    public PlayerStatus getPlayerStatus(String deviceId, String did) {
        log.info("获取播放状态, deviceId={}", deviceId);
        try {
            var ubusResult = minaService.playerGetStatus(deviceId);
            if (!ubusResult.success() || ubusResult.data() == null) {
                log.warn("ubus player_get_play_status 失败: {}", ubusResult.error());
                return new PlayerStatus(0, 0, "", "", 0, 0, 0);
            }
            // 解析 data.info 中的 JSON 字符串
            String infoStr = ubusResult.data().path("info").asText("");
            log.info("ubus data.info 原始内容: {}", infoStr);
            if (infoStr.isEmpty()) {
                log.warn("ubus 返回 data.info 为空");
                return new PlayerStatus(0, 0, "", "", 0, 0, 0);
            }
            JsonNode info = new com.fasterxml.jackson.databind.ObjectMapper().readTree(infoStr);
            JsonNode detail = info.path("play_song_detail");
            int status = info.path("status").asInt(0);
            int volume = info.path("volume").asInt(0);
            String mediaType = info.path("media_type").asText("");
            String mediaId = detail.path("audio_id").asText("");
            long playTime = detail.path("position").asLong(0) / 1000;
            long duration = detail.path("duration").asLong(0) / 1000;
            int loopType = info.path("loop_type").asInt(0);
            log.info("播放状态: status={}, volume={}, loopType={}", status, volume, loopType);
            return new PlayerStatus(status, volume, mediaType, mediaId, playTime, duration, loopType);
        } catch (Exception e) {
            log.error("获取播放状态异常", e);
            return new PlayerStatus(0, 0, "", "", 0, 0, 0);
        }
    }
}
