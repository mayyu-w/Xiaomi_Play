package com.xiaomi.sdk.mina;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomi.sdk.account.MiAccountService;
import com.xiaomi.sdk.config.XiaomiSdkProperties;
import com.xiaomi.sdk.crypto.CryptoService;
import com.xiaomi.sdk.exception.XiaomiApiException;
import com.xiaomi.sdk.http.OkHttpClientFactory;
import com.xiaomi.sdk.model.Device;
import com.xiaomi.sdk.model.TtsResult;
import okhttp3.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MiNA 服务 — TTS 语音播报 + 媒体控制
 * 通过 ubus 协议与小米音箱交互
 * @author awen
 */
public class MiNAService {

    private static final String SID = "micoapi";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CryptoService crypto;
    private final MiAccountService accountService;
    private final XiaomiSdkProperties properties;

    public MiNAService(OkHttpClient httpClient, ObjectMapper objectMapper,
                       CryptoService crypto, MiAccountService accountService,
                       XiaomiSdkProperties properties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.crypto = crypto;
        this.accountService = accountService;
        this.properties = properties;
    }

    /**
     * 文本转语音播报
     */
    public TtsResult textToSpeech(String deviceId, String text) {
        boolean success = ubusRequest(deviceId, "text_to_speech", "mibrain",
                Map.of("text", text));
        return success ? TtsResult.ok() : TtsResult.fail("TTS failed");
    }

    /**
     * 设置音量
     */
    public boolean playerSetVolume(String deviceId, int volume) {
        Map<String, Object> message = new HashMap<>();
        message.put("volume", volume);
        message.put("media", "app_ios");
        return ubusRequest(deviceId, "player_set_volume", "mediaplayer", message);
    }

    /**
     * 暂停播放
     */
    public boolean playerPause(String deviceId) {
        return ubusRequest(deviceId, "player_pause", "mediaplayer",
                Map.of("media", "app_ios"));
    }

    /**
     * 停止播放
     */
    public boolean playerStop(String deviceId) {
        return ubusRequest(deviceId, "player_stop", "mediaplayer",
                Map.of("media", "app_ios"));
    }

    /**
     * 通过 URL 播放音频
     */
    public boolean playByUrl(String deviceId, String url) {
        Map<String, Object> message = new HashMap<>();
        message.put("media", "app_ios");
        message.put("src", url);
        message.put("type", 1);
        return ubusRequest(deviceId, "player_play_music", "mediaplayer", message);
    }

    /**
     * 通过音乐 URL 播放（带 audioId）
     */
    public boolean playByMusicUrl(String deviceId, String url, int type, String audioId) {
        Map<String, Object> message = new HashMap<>();
        message.put("media", "app_ios");
        message.put("src", url);
        message.put("type", type);
        if (audioId != null && !audioId.isEmpty()) {
            message.put("audio_id", audioId);
        }
        return ubusRequest(deviceId, "player_play_music", "mediaplayer", message);
    }

    /**
     * 获取播放器状态
     */
    public JsonNode playerGetStatus(String deviceId) {
        ensureLoggedIn();
        try {
            String requestId = "app_ios_" + crypto.getRandomString(30);
            String url = properties.api().naUrl2()
                    + "/mediastatus?deviceId=" + deviceId
                    + "&requestId=" + requestId;

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", OkHttpClientFactory.getUserAgentMina())
                    .header("Cookie", accountService.buildCookieString(SID))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return parseResponse(response);
            }
        } catch (XiaomiApiException e) {
            throw e;
        } catch (Exception e) {
            throw new XiaomiApiException(500, "API_002", "Failed to get player status", e);
        }
    }

    /**
     * 获取设备列表（MiNA API）
     */
    public List<Device> deviceList() {
        return accountService.getDeviceList();
    }

    // ---- 内部方法 ----

    private boolean ubusRequest(String deviceId, String method, String path,
                                Map<String, Object> message) {
        ensureLoggedIn();
        try {
            String requestId = "app_ios_" + crypto.getRandomString(30);
            String messageJson = objectMapper.writeValueAsString(message);

            Map<String, Object> body = new HashMap<>();
            body.put("deviceId", deviceId);
            body.put("method", method);
            body.put("message", messageJson);
            body.put("path", path);
            body.put("requestId", requestId);

            String url = properties.api().naUrl2() + "/remote/ubus";

            RequestBody requestBody = RequestBody.create(
                    objectMapper.writeValueAsString(body),
                    MediaType.get("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("User-Agent", OkHttpClientFactory.getUserAgentMina())
                    .header("Cookie", accountService.buildCookieString(SID))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                JsonNode json = parseResponse(response);
                return json.path("code").asInt(-1) == 0;
            }
        } catch (XiaomiApiException e) {
            throw e;
        } catch (Exception e) {
            throw new XiaomiApiException(500, "API_002",
                    "ubus request failed: " + method, e);
        }
    }

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
