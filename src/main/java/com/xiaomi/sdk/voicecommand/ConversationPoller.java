package com.xiaomi.sdk.voicecommand;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomi.sdk.account.MiAccountService;
import com.xiaomi.sdk.exception.XiaomiApiException;
import com.xiaomi.sdk.exception.XiaomiAuthException;
import com.xiaomi.sdk.model.VoiceCommandResult;
import com.xiaomi.sdk.model.Device;
import jakarta.annotation.PreDestroy;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
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

/**
 * 对话记录轮询服务
 * 轮询小爱 API 获取用户对音箱的语音输入，匹配命令并执行
 * @author awen
 */
public class ConversationPoller {

    private static final Logger log = LoggerFactory.getLogger(ConversationPoller.class);
    private static final String CONVERSATION_API =
            "https://userprofile.mina.mi.com/device_profile/v2/conversation?source=dialogu&hardware=%s&timestamp=%d&limit=2";

    private final MiAccountService accountService;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final VoiceCommandHandler commandHandler;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private volatile ScheduledFuture<?> task;
    private volatile String activeDeviceId;
    private volatile String activeHardware;
    private volatile long lastTimestamp;
    private volatile int intervalSeconds = 1;
    private volatile boolean tokenExpired = false;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public ConversationPoller(MiAccountService accountService,
                              ObjectMapper objectMapper,
                              OkHttpClient httpClient,
                              VoiceCommandHandler commandHandler) {
        this.accountService = accountService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.commandHandler = commandHandler;
    }

    public synchronized void start(String deviceId, int intervalSeconds) {
        this.activeDeviceId = deviceId;
        this.intervalSeconds = intervalSeconds;
        this.lastTimestamp = System.currentTimeMillis();
        this.tokenExpired = false;
        resolveHardware(deviceId);
        commandHandler.refreshCustomKeywords();
        log.info("对话轮询启动: deviceId={}, hardware={}, 间隔={}秒", deviceId, activeHardware, intervalSeconds);
        reschedule();
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        activeDeviceId = null;
        log.info("对话轮询已停止");
    }

    public boolean isRunning() {
        return task != null && !task.isCancelled() && !task.isDone();
    }

    public String getActiveDeviceId() {
        return activeDeviceId;
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    // ---- SSE ----

    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    private void broadcastEvent(Map<String, Object> payload) {
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("conversation").data(payload));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }

    // ---- 轮询 ----

    private void reschedule() {
        if (task != null) {
            task.cancel(false);
        }
        if (activeDeviceId == null || activeDeviceId.isEmpty()) return;
        task = executor.scheduleAtFixedRate(this::pollOnce, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    private void resolveHardware(String deviceId) {
        try {
            List<Device> devices = accountService.getDeviceList();
            for (Device d : devices) {
                if (deviceId.equals(d.deviceId())) {
                    this.activeHardware = d.hardware();
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("解析设备硬件信息失败: {}", e.getMessage());
        }
        this.activeHardware = "";
    }

    private void pollOnce() {
        if (activeDeviceId == null || tokenExpired) return;
        try {
            ConversationRecord record = fetchLatestConversation();
            if (record == null) return;

            log.debug("获取到对话: query={}, timestamp={}", record.query, record.timestamp);

            VoiceCommandResult result = commandHandler.handle(record.query, activeDeviceId);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("timestamp", record.timestamp);
            payload.put("query", record.query);
            payload.put("answer", record.answer);
            payload.put("matchedCommand", result.command());
            payload.put("handled", result.success());
            broadcastEvent(payload);

        } catch (XiaomiAuthException e) {
            handleTokenExpired();
        } catch (Exception e) {
            log.debug("对话轮询异常: {}", e.getMessage());
        }
    }

    private ConversationRecord fetchLatestConversation() throws IOException {
        String url = String.format(CONVERSATION_API, activeHardware, lastTimestamp);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("Cookie", accountService.buildCookieString("micoapi")
                        + "; deviceId=" + activeDeviceId)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 401) {
                throw new XiaomiAuthException("AUTH_005", "对话 API 会话过期");
            }
            if (!response.isSuccessful()) {
                log.debug("对话API返回非200: {}", response.code());
                return null;
            }

            String body = response.body() != null ? response.body().string() : "{}";
            JsonNode json = objectMapper.readTree(body);
            JsonNode dataNode = json.get("data");
            if (dataNode == null || dataNode.isNull()) return null;

            // data 是双重 JSON：外层 JSON → data 字符串 → 再解析
            String dataStr = dataNode.asText();
            JsonNode data = objectMapper.readTree(dataStr);
            JsonNode records = data.get("records");
            if (records == null || !records.isArray() || records.isEmpty()) return null;

            JsonNode latest = records.get(0);
            long time = latest.path("time").asLong(0);
            if (time <= lastTimestamp) return null;

            lastTimestamp = time;
            String query = latest.path("query").asText("").trim();
            String answer = "";
            JsonNode answers = latest.get("answers");
            if (answers != null && answers.isArray() && !answers.isEmpty()) {
                answer = answers.get(0).path("tts").path("text").asText("").trim();
            }

            return new ConversationRecord(time, query, answer);
        }
    }

    private void handleTokenExpired() {
        log.warn("对话API检测到401，尝试自动刷新Token");
        try {
            accountService.refreshToken();
            tokenExpired = false;
            log.info("Token刷新成功，恢复对话轮询");
        } catch (Exception e) {
            tokenExpired = true;
            log.error("Token刷新失败，对话轮询已暂停: {}", e.getMessage());
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

    public record ConversationRecord(long timestamp, String query, String answer) {}
}
