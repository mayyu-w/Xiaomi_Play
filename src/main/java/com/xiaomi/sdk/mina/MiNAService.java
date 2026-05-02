package com.xiaomi.sdk.mina;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomi.sdk.account.MiAccountService;
import com.xiaomi.sdk.config.XiaomiSdkProperties;
import com.xiaomi.sdk.crypto.CryptoService;
import com.xiaomi.sdk.exception.XiaomiApiException;
import com.xiaomi.sdk.exception.XiaomiAuthException;
import com.xiaomi.sdk.http.OkHttpClientFactory;
import com.xiaomi.sdk.model.Device;
import com.xiaomi.sdk.model.TtsResult;
import com.xiaomi.sdk.miot.MiIOService;
import okhttp3.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * MiNA 服务 — TTS 语音播报 + 媒体控制
 * 通过 ubus 协议与小米音箱交互
 * @author awen
 */
public class MiNAService {

    private static final Logger log = LoggerFactory.getLogger(MiNAService.class);
    private static final String SID = "micoapi";

    /** 需要使用 player_play_music API 播放的设备型号（源自 miservice-fork） */
    private static final Set<String> USE_PLAY_MUSIC_API = Set.of(
            "LX04", "LX05", "L05B", "L05C", "L06", "L06A",
            "X08A", "X10A", "X08C", "X08E", "X8F", "X4B",
            "OH2", "OH2P", "X6A"
    );

    /** 需要通过 MIoT action 执行 TTS 的设备型号 → siid-aiid */
    private static final Map<String, int[]> TTS_COMMAND = Map.ofEntries(
            Map.entry("OH2", new int[]{5, 3}),
            Map.entry("OH2P", new int[]{7, 3}),
            Map.entry("LX06", new int[]{5, 1}),
            Map.entry("S12", new int[]{5, 1}),
            Map.entry("L15A", new int[]{7, 3}),
            Map.entry("LX5A", new int[]{5, 1}),
            Map.entry("LX01", new int[]{5, 1}),
            Map.entry("LX05", new int[]{5, 1}),
            Map.entry("X10A", new int[]{7, 3}),
            Map.entry("L17A", new int[]{7, 3}),
            Map.entry("ASX4B", new int[]{5, 3}),
            Map.entry("L06A", new int[]{5, 1}),
            Map.entry("L05B", new int[]{5, 3}),
            Map.entry("L05C", new int[]{5, 3}),
            Map.entry("X6A", new int[]{7, 3}),
            Map.entry("X08E", new int[]{7, 3}),
            Map.entry("L09A", new int[]{3, 1}),
            Map.entry("LX04", new int[]{5, 1})
    );

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CryptoService crypto;
    private final MiAccountService accountService;
    private final XiaomiSdkProperties properties;
    private MiIOService miioService;

    public MiNAService(OkHttpClient httpClient, ObjectMapper objectMapper,
                       CryptoService crypto, MiAccountService accountService,
                       XiaomiSdkProperties properties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.crypto = crypto;
        this.accountService = accountService;
        this.properties = properties;
    }

    public void setMiioService(MiIOService miioService) {
        this.miioService = miioService;
    }

    /**
     * 文本转语音播报
     * 根据设备硬件型号自动选择 MiNA ubus 或 MIoT action 方式
     */
    public TtsResult textToSpeech(String deviceId, String text) {
        // 查找设备信息以获取 hardware 和 did
        String hardware = null;
        String did = null;
        try {
            List<Device> devices = accountService.getDeviceList();
            for (Device d : devices) {
                if (deviceId.equals(d.deviceId())) {
                    hardware = d.hardware();
                    did = d.did();
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("获取设备列表失败，使用 MiNA TTS: {}", e.getMessage());
        }

        if (hardware != null && TTS_COMMAND.containsKey(hardware) && miioService != null && did != null) {
            int[] siidAiid = TTS_COMMAND.get(hardware);
            String ttsText = text.replace(" ", ",");
            log.info("设备 {} (hardware={}) 使用 MIoT action TTS: siid={}, aiid={}, text={}",
                    deviceId, hardware, siidAiid[0], siidAiid[1], ttsText);
            try {
                miioService.executeAction(did, siidAiid[0], siidAiid[1], List.of(ttsText));
                return TtsResult.ok();
            } catch (Exception e) {
                log.error("MIoT action TTS 失败: {}", e.getMessage());
                return TtsResult.fail("MIoT TTS 失败: " + e.getMessage());
            }
        }

        UbusResult result = ubusRequest(deviceId, "text_to_speech", "mibrain",
                Map.of("text", text));
        return result.success() ? TtsResult.ok() : TtsResult.fail(result.error());
    }

    /**
     * 设置音量
     */
    public boolean playerSetVolume(String deviceId, int volume) {
        return ubusRequest(deviceId, "player_set_volume", "mediaplayer",
                Map.of("volume", volume, "media", "app_ios")).success();
    }

    /**
     * 暂停播放（使用 player_play_operation，兼容所有型号）
     */
    public boolean playerPause(String deviceId) {
        return ubusRequest(deviceId, "player_play_operation", "mediaplayer",
                Map.of("action", "pause", "media", "app_ios")).success();
    }

    /**
     * 停止播放（使用 player_play_operation，兼容所有型号）
     */
    public boolean playerStop(String deviceId) {
        return ubusRequest(deviceId, "player_play_operation", "mediaplayer",
                Map.of("action", "stop", "media", "app_ios")).success();
    }

    /**
     * 恢复播放
     */
    public boolean playerPlay(String deviceId) {
        return ubusRequest(deviceId, "player_play_operation", "mediaplayer",
                Map.of("action", "play", "media", "app_ios")).success();
    }

    /**
     * 设置播放模式（循环类型）
     */
    public boolean playerSetLoop(String deviceId, int type) {
        return ubusRequest(deviceId, "player_set_loop", "mediaplayer",
                Map.of("media", "common", "type", type)).success();
    }

    /**
     * 通过 URL 播放音频
     * 根据设备型号自动选择 player_play_url 或 player_play_music
     */
    public boolean playByUrl(String deviceId, String url) {
        String hardware = getDeviceHardware(deviceId);
        if (hardware != null && USE_PLAY_MUSIC_API.contains(hardware)) {
            log.info("设备 {} (hardware={}) 使用 play_by_music_url", deviceId, hardware);
            return playByMusicUrl(deviceId, url, 1, "1582971365183456177");
        }
        return ubusRequest(deviceId, "player_play_url", "mediaplayer",
                Map.of("url", url, "type", 2, "media", "app_ios")).success();
    }

    /**
     * 通过音乐 URL 播放（player_play_music，用于特定型号音箱）
     * payload 结构完全对齐 miservice-fork 的 play_by_music_url
     */
    public boolean playByMusicUrl(String deviceId, String url, int type, String audioId) {
        String audioType = (type == 1) ? "MUSIC" : "";

        Map<String, Object> music = Map.of(
                "payload", Map.of(
                        "audio_type", audioType,
                        "audio_items", List.of(Map.of(
                                "item_id", Map.of(
                                        "audio_id", audioId,
                                        "cp", Map.of(
                                                "album_id", "-1",
                                                "episode_index", 0,
                                                "id", "355454500",
                                                "name", "xiaowei"
                                        )
                                ),
                                "stream", Map.of("url", url)
                        )),
                        "list_params", Map.of(
                                "listId", "-1",
                                "loadmore_offset", 0,
                                "origin", "xiaowei",
                                "type", "MUSIC"
                        )
                ),
                "play_behavior", "REPLACE_ALL"
        );

        String musicJson;
        try {
            musicJson = objectMapper.writeValueAsString(music);
        } catch (Exception e) {
            log.error("序列化 music JSON 失败", e);
            return false;
        }

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("startaudioid", audioId);
        message.put("music", musicJson);

        return ubusRequest(deviceId, "player_play_music", "mediaplayer", message).success();
    }

    /**
     * 获取播放器状态（通过 ubus player_get_play_status）
     */
    public UbusResult playerGetStatus(String deviceId) {
        return ubusRequest(deviceId, "player_get_play_status", "mediaplayer",
                Map.of("media", "app_ios"));
    }

    /**
     * 获取设备列表（MiNA API）
     */
    public List<Device> deviceList() {
        return accountService.getDeviceList();
    }

    // ---- 内部方法 ----

    private String getDeviceHardware(String deviceId) {
        try {
            List<Device> devices = accountService.getDeviceList();
            for (Device d : devices) {
                if (deviceId.equals(d.deviceId())) {
                    return d.hardware();
                }
            }
        } catch (Exception e) {
            log.warn("获取设备硬件信息失败: {}", e.getMessage());
        }
        return null;
    }

    public UbusResult sendUbus(String deviceId, String method, String path,
                             Map<String, Object> message) {
        return ubusRequest(deviceId, method, path, message);
    }

    private UbusResult ubusRequest(String deviceId, String method, String path,
                                Map<String, Object> message) {
        ensureLoggedIn();
        try {
            String requestId = "app_ios_" + crypto.getRandomString(30);
            String messageJson = objectMapper.writeValueAsString(message);

            FormBody.Builder formBuilder = new FormBody.Builder()
                    .add("deviceId", deviceId)
                    .add("method", method)
                    .add("message", messageJson)
                    .add("path", path)
                    .add("requestId", requestId);

            String url = properties.api().naUrl2() + "/remote/ubus";

            Request request = new Request.Builder()
                    .url(url)
                    .post(formBuilder.build())
                    .header("User-Agent", OkHttpClientFactory.getUserAgentMina())
                    .header("Cookie", accountService.buildCookieString(SID))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
//                log.info("ubus {} response: status={}, body={}", method, response.code(), respBody);
                if (response.code() == 401) {
                    throw new XiaomiAuthException("AUTH_005", "MiNA session 已过期，请重新登录");
                }
                JsonNode json = objectMapper.readTree(respBody);
                int code = json.path("code").asInt(-1);
                if (code == 0) {
                    return new UbusResult(true, null, json.get("data"));
                }
                String errorMsg = json.path("message").asText("")
                        + (json.has("data") ? " | data=" + json.get("data") : "");
                return new UbusResult(false, "code=" + code + " " + errorMsg, null);
            }
        } catch (XiaomiApiException e) {
            throw e;
        } catch (Exception e) {
            throw new XiaomiApiException(500, "API_002",
                    "ubus request failed: " + method, e);
        }
    }

    public record UbusResult(boolean success, String error, JsonNode data) {}

    private JsonNode parseResponse(Response response) throws IOException {
        String body = response.body() != null ? response.body().string() : "{}";
        JsonNode json = objectMapper.readTree(body);
        if (json.has("code")) {
            int code = json.get("code").asInt();
            if (code != 0) {
                String message = json.has("message") ? json.get("message").asText() : "Unknown";
                if (message.toLowerCase().contains("auth")) {
                    throw new XiaomiApiException(401, "AUTH_004", message);
                }
            }
        }
        return json;
    }

    private void ensureLoggedIn() {
        if (accountService.getCurrentToken() == null) {
            throw new XiaomiApiException(401, "AUTH_004", "Not logged in");
        }
    }
}
