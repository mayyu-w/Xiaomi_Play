package com.xiaomi.sdk.account;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomi.sdk.config.XiaomiSdkProperties;
import com.xiaomi.sdk.crypto.CryptoService;
import com.xiaomi.sdk.http.OkHttpClientFactory;
import com.xiaomi.sdk.model.LoginResult;
import com.xiaomi.sdk.token.TokenManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 小米二维码登录服务
 * 流程: serviceLogin → longPolling/loginUrl → 轮询 lp → 回调获取 serviceToken
 * 参考: https://github.com/hanxi/xiaomusic/blob/main/xiaomusic/qrcode_login.py
 * @author awen
 */
public class QrCodeLoginService {

    private static final Logger log = LoggerFactory.getLogger(QrCodeLoginService.class);
    private static final String SID_MICOAPI = "micoapi";
    private static final long SESSION_TIMEOUT_MS = 5 * 60 * 1000;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CryptoService crypto;
    private final XiaomiSdkProperties properties;
    private final MiAccountService accountService;
    private final String deviceId;

    private final ConcurrentHashMap<String, QrCodeSession> sessions = new ConcurrentHashMap<>();

    public QrCodeLoginService(OkHttpClient httpClient, ObjectMapper objectMapper,
                              CryptoService crypto, XiaomiSdkProperties properties,
                              MiAccountService accountService) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.crypto = crypto;
        this.properties = properties;
        this.accountService = accountService;
        this.deviceId = crypto.getRandomString(16).toUpperCase();
    }

    static class QrCodeSession {
        final String lpUrl;
        final long createdAt;
        final String ipAddress;
        final String userAgent;
        volatile String status = "waiting";
        volatile String message;
        volatile LoginResult loginResult;

        QrCodeSession(String lpUrl, String ipAddress, String userAgent) {
            this.lpUrl = lpUrl;
            this.createdAt = System.currentTimeMillis();
            this.ipAddress = ipAddress;
            this.userAgent = userAgent;
        }
    }

    /**
     * 初始化二维码登录，返回二维码图片 URL 和会话 ID
     */
    public Map<String, Object> initQrLogin() throws Exception {
        cleanupExpiredSessions();

        // Step 1: serviceLogin 获取登录参数（不带认证 cookie）
        JsonNode loginResp = callServiceLogin();

        if (loginResp.has("code") && loginResp.get("code").asInt() == 0) {
            return Map.of("alreadyLoggedIn", true, "message", "已登录，无需扫码");
        }

        // Step 2: longPolling/loginUrl 获取二维码
        String qs = loginResp.get("qs").asText();
        String sid = loginResp.get("sid").asText();
        String sign = loginResp.get("_sign").asText();
        String callback = loginResp.get("callback").asText();

        JsonNode qrResp = callLongPollUrl(qs, sid, sign, callback);
        String qrImageUrl = qrResp.get("qr").asText();
        String lpUrl = qrResp.get("lp").asText();

        // Step 3: 创建会话并启动后台轮询
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        String ip = null;
        String ua = null;
        try {
            HttpServletRequest req = ((ServletRequestAttributes)
                    RequestContextHolder.currentRequestAttributes()).getRequest();
            ip = req.getRemoteAddr();
            ua = req.getHeader("User-Agent");
        } catch (Exception ignored) {}
        QrCodeSession session = new QrCodeSession(lpUrl, ip, ua);
        sessions.put(sessionId, session);

        startPolling(sessionId);

        log.info("二维码登录会话已创建: {}", sessionId.substring(0, 8));
        return Map.of(
                "sessionId", sessionId,
                "qrImageUrl", qrImageUrl,
                "expireSeconds", 300
        );
    }

    /**
     * 检查二维码扫码状态
     */
    public Map<String, Object> checkStatus(String sessionId) {
        QrCodeSession session = sessions.get(sessionId);
        if (session == null) {
            return Map.of("status", "error", "message", "会话不存在或已过期");
        }

        if ("waiting".equals(session.status)
                && System.currentTimeMillis() - session.createdAt > SESSION_TIMEOUT_MS) {
            session.status = "expired";
            session.message = "二维码已过期，请刷新重试";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", session.status);
        if (session.message != null) {
            result.put("message", session.message);
        }

        if ("confirmed".equals(session.status) && session.loginResult != null) {
            result.put("userId", session.loginResult.userId());
            sessions.remove(sessionId);
        }

        return result;
    }

    private void startPolling(String sessionId) {
        Thread.ofVirtual().name("qr-poll-" + sessionId.substring(0, 8)).start(() -> {
            QrCodeSession session = sessions.get(sessionId);
            if (session == null) return;

            try {
                OkHttpClient longPollClient = httpClient.newBuilder()
                        .readTimeout(Duration.ofSeconds(180))
                        .build();

                Request request = new Request.Builder()
                        .url(session.lpUrl)
                        .get()
                        .header("User-Agent", OkHttpClientFactory.getUserAgentAccount())
                        .build();

                try (Response response = longPollClient.newCall(request).execute()) {
                    JsonNode lpData = parseXiaomiResponse(response);

                    if (lpData.has("code") && lpData.get("code").asInt() != 0) {
                        session.status = "error";
                        session.message = lpData.has("desc")
                                ? lpData.get("desc").asText() : "扫码失败";
                        return;
                    }

                    String userId = lpData.get("userId").asText();
                    String passToken = lpData.get("passToken").asText();
                    String ssecurity = lpData.get("ssecurity").asText();
                    String nonce = lpData.get("nonce").asText();
                    String location = lpData.get("location").asText();

                    String clientSign = crypto.computeClientSign(nonce, ssecurity);
                    String serviceToken = fetchServiceTokenFromCallback(
                            location, nonce, clientSign, userId, passToken);

                    long expire = System.currentTimeMillis() + 30L * 24 * 3600 * 1000;
                    LoginResult partialResult = new LoginResult(
                            userId, passToken, ssecurity, serviceToken, expire, "", expire
                    );

                    LoginResult fullResult;
                    try {
                        TokenManager.setAuditContext(session.ipAddress, session.userAgent);
                        fullResult = accountService.saveQrLoginResult(partialResult);
                    } finally {
                        TokenManager.clearAuditContext();
                    }

                    session.status = "confirmed";
                    session.loginResult = fullResult;
                    log.info("二维码登录成功, userId={}", userId);
                }
            } catch (SocketTimeoutException e) {
                session.status = "expired";
                session.message = "二维码已过期，请刷新重试";
            } catch (Exception e) {
                log.error("二维码登录轮询失败", e);
                session.status = "error";
                session.message = "登录失败：" + e.getMessage();
            }
        });
    }

    // ---- HTTP 方法 ----

    private JsonNode callServiceLogin() throws IOException {
        String url = properties.api().baseUrl()
                + "/pass/serviceLogin?sid=" + SID_MICOAPI + "&_json=true";

        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", OkHttpClientFactory.getUserAgentAccount())
                .header("Cookie", "sdkVersion=3.9; deviceId=" + deviceId)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return parseXiaomiResponse(response);
        }
    }

    private JsonNode callLongPollUrl(String qs, String sid, String sign, String callback)
            throws IOException {
        String url = properties.api().baseUrl() + "/longPolling/loginUrl"
                + "?qs=" + URLEncoder.encode(qs, StandardCharsets.UTF_8)
                + "&sid=" + URLEncoder.encode(sid, StandardCharsets.UTF_8)
                + "&_sign=" + URLEncoder.encode(sign, StandardCharsets.UTF_8)
                + "&callback=" + URLEncoder.encode(callback, StandardCharsets.UTF_8)
                + "&theme=&bizDeviceType=&_hasLogo=false&_qrsize=240"
                + "&_dc=" + System.currentTimeMillis();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", OkHttpClientFactory.getUserAgentAccount())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return parseXiaomiResponse(response);
        }
    }

    /**
     * 从 securityTokenService 回调获取 serviceToken
     * 与密码登录流程一致：拼接 clientSign + 发送 passToken cookie
     */
    private String fetchServiceTokenFromCallback(String location, String nonce,
                                                  String clientSign, String userId,
                                                  String passToken) throws IOException {
        String url = location + "&clientSign=" + URLEncoder.encode(clientSign, StandardCharsets.UTF_8);

        String cookie = "sdkVersion=3.9; deviceId=" + deviceId
                + "; userId=" + userId
                + "; passToken=" + passToken;

        // 先尝试 followRedirects=true（serviceToken 可能在最终响应的 Set-Cookie 中）
        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", OkHttpClientFactory.getUserAgentAccount())
                .header("Cookie", cookie)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            for (String header : response.headers("Set-Cookie")) {
                if (header.startsWith("serviceToken=")) {
                    int end = header.indexOf(';', "serviceToken=".length());
                    return end > 0
                            ? header.substring("serviceToken=".length(), end)
                            : header.substring("serviceToken=".length());
                }
            }
        }

        // 如果 followRedirects 没拿到，手动跟踪重定向
        OkHttpClient noRedirectClient = httpClient.newBuilder()
                .followRedirects(false)
                .build();

        String currentUrl = url;
        for (int i = 0; i < 5; i++) {
            request = new Request.Builder()
                    .url(currentUrl)
                    .get()
                    .header("User-Agent", OkHttpClientFactory.getUserAgentAccount())
                    .header("Cookie", cookie)
                    .build();

            try (Response response = noRedirectClient.newCall(request).execute()) {
                for (String header : response.headers("Set-Cookie")) {
                    if (header.startsWith("serviceToken=")) {
                        int end = header.indexOf(';', "serviceToken=".length());
                        return end > 0
                                ? header.substring("serviceToken=".length(), end)
                                : header.substring("serviceToken=".length());
                    }
                }

                if (response.isRedirect()) {
                    String redirectUrl = response.header("Location");
                    if (redirectUrl != null) {
                        currentUrl = redirectUrl;
                        continue;
                    }
                }
                break;
            }
        }
        throw new IOException("未获取到 serviceToken");
    }

    private JsonNode parseXiaomiResponse(Response response) throws IOException {
        String body = response.body() != null ? response.body().string() : "";
        if (body.startsWith("&&&START&&&")) {
            body = body.substring(11);
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new IOException("Failed to parse response: " + body, e);
        }
    }

    private void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry ->
                now - entry.getValue().createdAt > SESSION_TIMEOUT_MS * 2
        );
    }
}
