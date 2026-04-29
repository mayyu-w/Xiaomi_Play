package com.xiaomi.sdk.music;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiaomi.sdk.mina.MiNAService;
import com.xiaomi.sdk.model.MusicResult;
import com.xiaomi.sdk.model.PlayerStatus;

/**
 * 音乐播放控制服务（源自 xiaomusic）
 * 基于 MiNAService 的 ubus 协议实现
 * @author awen
 */
public class MusicService {

    private final MiNAService minaService;

    public MusicService(MiNAService minaService) {
        this.minaService = minaService;
    }

    /**
     * 播放
     */
    public MusicResult play(String deviceId, String url) {
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
     * 获取播放器状态
     */
    public PlayerStatus getPlayerStatus(String deviceId) {
        try {
            JsonNode status = minaService.playerGetStatus(deviceId);
            JsonNode info = objectMapperReadInfo(status);
            return new PlayerStatus(
                    info.path("status").asInt(0),
                    info.path("volume").asInt(0),
                    info.path("media_type").asText(""),
                    info.path("media_id").asText(""),
                    info.path("play_song_time").asLong(0),
                    info.path("duration").asLong(0)
            );
        } catch (Exception e) {
            return new PlayerStatus(0, 0, "", "", 0, 0);
        }
    }

    private JsonNode objectMapperReadInfo(JsonNode status) {
        try {
            String infoStr = status.path("data").path("info").asText("{}");
            return com.fasterxml.jackson.databind.json.JsonMapper.builder()
                    .build().readTree(infoStr);
        } catch (Exception e) {
            return com.fasterxml.jackson.databind.node.NullNode.getInstance();
        }
    }
}
