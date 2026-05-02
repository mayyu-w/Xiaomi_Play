package com.xiaomi.sdk.account;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomi.sdk.config.XiaomiSdkProperties;
import com.xiaomi.sdk.crypto.CryptoService;
import com.xiaomi.sdk.exception.XiaomiApiException;
import com.xiaomi.sdk.exception.XiaomiAuthException;
import com.xiaomi.sdk.http.OkHttpClientFactory;
import com.xiaomi.sdk.model.Device;
import com.xiaomi.sdk.model.LoginResult;
import com.xiaomi.sdk.token.TokenManager;
import okhttp3.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 小米账号认证服务
 * 实现三步登录流程: serviceLogin → serviceLoginAuth2 → securityTokenService
 * @author awen
 */
public class MiAccountService {

    private static final Logger log = LoggerFactory.getLogger(MiAccountService.class);
    private static final String ACCOUNT_PATH = "/pass/";
    private static final String SID_XIAOMIIO = "xiaomiio";
    private static final String SID_MICOAPI = "micoapi";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CryptoService crypto;
    private final XiaomiSdkProperties properties;
    private final TokenManager tokenManager;

    private String deviceId;

    /** 内存缓存，避免高频请求时反复查 Redis/DB */
    private LoginResult currentToken;

    /** xiaomiio SID 返回的 ssecurity，用于 MIoT 签名 */
    private String ioSsecurity;

    /** 用户昵称缓存（1 小时） */
    private String cachedNickname;
    private long nicknameCacheTime;

    public MiAccountService(OkHttpClient httpClient, ObjectMapper objectMapper,
                            CryptoService crypto, XiaomiSdkProperties properties,
                            TokenManager tokenManager) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.crypto = crypto;
        this.properties = properties;
        this.tokenManager = tokenManager;
        this.deviceId = crypto.getRandomString(16).toUpperCase();
    }

    /**
     * 密码登录
     */
    public LoginResult login(String username, String password) {
        return doLogin(username, password, "LOGIN", "密码登录");
    }

    /**
     * 内部登录实现，支持自定义 action 和 detail
     */
    private LoginResult doLogin(String username, String password, String action, String detail) {
        try {
            // 尝试恢复已有 Token 用于 cookie 构建（会话续期）
            if (currentToken == null) {
                LoginResult cached = tokenManager.loadActiveToken();
                if (cached != null) {
                    currentToken = cached;
                }
            }

            // Step 1: serviceLogin — 检查已有 session
            JsonNode loginResp = serviceLogin(SID_MICOAPI, null);

            if (loginResp.has("code") && loginResp.get("code").asInt() != 0) {
                // Step 2: serviceLoginAuth2 — 提交用户名密码
                String hash = crypto.md5(password);
                Map<String, String> authData = new HashMap<>();
                authData.put("_json", "true");
                authData.put("qs", loginResp.get("qs").asText());
                authData.put("sid", loginResp.get("sid").asText());
                authData.put("_sign", loginResp.get("_sign").asText());
                authData.put("callback", loginResp.get("callback").asText());
                authData.put("user", username);
                authData.put("hash", hash);

                loginResp = serviceLoginAuth2(authData);
                if (loginResp.has("code") && loginResp.get("code").asInt() != 0) {
                    throw new XiaomiAuthException("AUTH_001", "账号或密码错误");
                }
            }

            String userId = loginResp.get("userId").asText();
            String passToken = loginResp.get("passToken").asText();
            String ssecurity = loginResp.get("ssecurity").asText();
            String nonce = loginResp.get("nonce").asText();
            String location = loginResp.get("location").asText();

            // Step 3: securityTokenService — 获取 micoapi serviceToken
            String clientSign = crypto.computeClientSign(nonce, ssecurity);
            String serviceToken = fetchServiceToken(location, nonce, clientSign);

            long expire = System.currentTimeMillis() + 30L * 24 * 3600 * 1000;
            // 临时设置 token，使后续 serviceLogin 能携带 passToken cookie
            this.currentToken = new LoginResult(
                    userId, passToken, ssecurity, serviceToken, expire, "", expire
            );

            // Step 4: 获取 xiaomiio SID 的 serviceToken（MIoT API 需要）
            String ioServiceToken = fetchServiceTokenForSid(SID_XIAOMIIO);

            this.currentToken = new LoginResult(
                    userId, passToken, ssecurity, serviceToken, expire, ioServiceToken, expire
            );

            // 持久化到 Redis + DB
            tokenManager.saveToken(this.currentToken, action, detail);
            return this.currentToken;

        } catch (XiaomiAuthException e) {
            throw e;
        } catch (Exception e) {
            this.currentToken = null;
            throw new XiaomiAuthException("AUTH_001", "登录失败，请重试", e);
        }
    }

    /**
     * 刷新 Token（使用 passToken 重新登录）
     */
    public LoginResult refreshToken() {
        LoginResult token = ensureTokenLoaded();
        if (token == null) {
            throw new XiaomiAuthException("AUTH_004", "No token to refresh");
        }
        return doLogin(token.userId(), token.passToken(), "REFRESH", "Token 自动刷新");
    }

    /**
     * 登出
     */
    public void logout() {
        String userId = currentToken != null ? currentToken.userId() : null;
        this.currentToken = null;
        tokenManager.removeToken(userId, "主动退出");
    }

    /**
     * 获取当前 Token（自动恢复 + 自动刷新）
     */
    public LoginResult getCurrentToken() {
        if (currentToken == null) {
            currentToken = tokenManager.loadActiveToken();
        }
        if (currentToken != null && tokenManager.isExpiringSoon(currentToken)) {
            try {
                currentToken = doLogin(currentToken.userId(), currentToken.passToken(),
                        "REFRESH", "Token 自动刷新");
            } catch (Exception e) {
                currentToken = null;
            }
        }
        // 按需补全 ioServiceToken
        if (currentToken != null
                && (currentToken.ioServiceToken() == null || currentToken.ioServiceToken().isEmpty())) {
            try {
                String ioToken = fetchServiceTokenForSid(SID_XIAOMIIO);
                if (!ioToken.isEmpty()) {
                    currentToken = new LoginResult(
                            currentToken.userId(), currentToken.passToken(), currentToken.ssecurity(),
                            currentToken.serviceToken(), currentToken.serviceTokenExpire(),
                            ioToken, currentToken.ioServiceTokenExpire()
                    );
                    tokenManager.saveToken(currentToken, "REFRESH", "补全ioServiceToken");
                    log.info("按需补全ioServiceToken成功");
                }
            } catch (Exception e) {
                log.warn("按需补全ioServiceToken失败: {}", e.getMessage());
            }
        }
        return currentToken;
    }

    /**
     * 获取设备列表（通过 MiNA API）
     */
    public List<Device> getDeviceList() {
        ensureLoggedIn();
        try {
            String requestId = "app_ios_" + crypto.getRandomString(30);
            String url = properties.api().naUrl2()
                    + "/admin/v2/device_list?master=0&requestId=" + requestId;

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", OkHttpClientFactory.getUserAgentMina())
                    .header("Cookie", buildCookieString(SID_MICOAPI))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                JsonNode json = parseResponse(response);
                JsonNode data = json.get("data");
                List<Device> devices = new ArrayList<>();
                if (data != null && data.isArray()) {
                    for (JsonNode item : data) {
                        devices.add(new Device(
                                item.path("miotDID").asText(""),
                                item.path("alias").asText(item.path("name").asText("")),
                                item.path("model").asText(""),
                                "",
                                item.path("deviceID").asText(""),
                                item.path("hardware").asText("")
                        ));
                    }
                }
                return devices;
            }
        } catch (XiaomiAuthException e) {
            throw e;
        } catch (Exception e) {
            throw new XiaomiApiException(500, "API_002", "Failed to get device list", e);
        }
    }

    public void setCurrentToken(LoginResult token) {
        this.currentToken = token;
    }

    /**
     * 保存二维码登录结果，补全 xiaomiio serviceToken 并持久化
     */
    public LoginResult saveQrLoginResult(LoginResult partialResult) {
        this.currentToken = partialResult;
        String ioServiceToken = "";
        try {
            ioServiceToken = fetchServiceTokenForSid(SID_XIAOMIIO);
            log.info("QR登录获取ioServiceToken: {}", ioServiceToken.isEmpty() ? "空" : "成功");
        } catch (Exception e) {
            log.warn("获取 xiaomiio serviceToken 失败: {}", e.getMessage());
        }

        LoginResult full = new LoginResult(
                partialResult.userId(), partialResult.passToken(), partialResult.ssecurity(),
                partialResult.serviceToken(), partialResult.serviceTokenExpire(),
                ioServiceToken, partialResult.ioServiceTokenExpire()
        );
        this.currentToken = full;
        tokenManager.saveToken(full, "QR_LOGIN", "二维码扫码登录");
        return full;
    }

    /**
     * 获取缓存的昵称，无缓存时触发后台异步获取（不阻塞）
     */
    public String getCachedNickname() {
        if (cachedNickname != null && System.currentTimeMillis() - nicknameCacheTime < 3600000) {
            return cachedNickname;
        }
        // 缓存过期或为空，异步刷新
        Thread.ofVirtual().name("nickname-fetch").start(() -> {
            try {
                refreshNickname();
            } catch (Exception ignored) {}
        });
        return cachedNickname;
    }

    private void refreshNickname() {
        LoginResult token = currentToken;
        if (token == null) {
            token = tokenManager.loadActiveToken();
        }
        if (token == null) return;

        try {
            String url = "https://api.account.xiaomi.com/pass/usersCard?ids=" + token.userId();
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", OkHttpClientFactory.getUserAgentAccount())
                    .header("Cookie", buildCookieString(SID_MICOAPI))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                JsonNode json = parseXiaomiResponse(response);
                String nick = null;
                String uid = token.userId();

                if (json.has("data")) {
                    JsonNode data = json.get("data");
                    if (data.isObject() && data.has(uid)) {
                        nick = data.get(uid).path("miliaoNick").asText(null);
                    } else if (data.isObject()) {
                        var fields = data.fields();
                        while (fields.hasNext()) {
                            var entry = fields.next();
                            nick = entry.getValue().path("miliaoNick").asText(null);
                            if (nick != null && !nick.isEmpty()) break;
                        }
                    }
                }
                if (nick == null && json.has("miliaoNick")) {
                    nick = json.get("miliaoNick").asText(null);
                }

                if (nick != null && !nick.isEmpty()) {
                    cachedNickname = nick;
                    nicknameCacheTime = System.currentTimeMillis();
                    log.info("获取用户昵称成功: {}", nick);
                }
            }
        } catch (Exception e) {
            log.warn("获取用户昵称失败: {}", e.getMessage());
        }
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    /**
     * 获取 MIoT 签名用的 ssecurity（优先使用 xiaomiio SID 的值）
     * ssecurity 和 ioServiceToken 必须来自同一次 serviceLogin，否则签名不匹配
     */
    public String getSsecurityForMiot() {
        if (ioSsecurity == null && currentToken != null) {
            try {
                String newIoToken = fetchServiceTokenForSid(SID_XIAOMIIO);
                if (!newIoToken.isEmpty() && ioSsecurity != null) {
                    // 用新的 ssecurity-ioServiceToken 对更新 currentToken
                    currentToken = new LoginResult(
                            currentToken.userId(), currentToken.passToken(), currentToken.ssecurity(),
                            currentToken.serviceToken(), currentToken.serviceTokenExpire(),
                            newIoToken, currentToken.ioServiceTokenExpire()
                    );
                    log.info("已更新 ioServiceToken 以匹配新的 ioSsecurity");
                }
            } catch (Exception e) {
                log.warn("按需获取 xiaomiio ssecurity 失败: {}", e.getMessage());
            }
        }
        if (ioSsecurity != null && !ioSsecurity.isEmpty()) {
            return ioSsecurity;
        }
        return currentToken != null ? currentToken.ssecurity() : null;
    }

    /**
     * 构建指定 sid 的 Cookie 字符串
     */
    public String buildCookieString(String sid) {
        LoginResult token = getCurrentToken();
        if (token == null) return "";
        String svcToken = SID_XIAOMIIO.equals(sid)
                ? token.ioServiceToken()
                : token.serviceToken();
        StringBuilder sb = new StringBuilder();
        sb.append("sdkVersion=3.9; ");
        sb.append("deviceId=").append(deviceId).append("; ");
        sb.append("userId=").append(token.userId()).append("; ");
        sb.append("serviceToken=").append(svcToken).append("; ");
        return sb.toString();
    }

    // ---- 内部方法 ----

    private String fetchServiceTokenForSid(String sid) throws IOException {
        JsonNode resp = serviceLogin(sid, null);
        if (resp.has("code") && resp.get("code").asInt() != 0) {
            return "";
        }
        if (!resp.has("location")) {
            return "";
        }
        String nonce = resp.path("nonce").asText("");
        String ssecurity = resp.path("ssecurity").asText(currentToken.ssecurity());
        // 存储 xiaomiio SID 的 ssecurity，用于 MIoT 签名
        if (SID_XIAOMIIO.equals(sid) && resp.has("ssecurity")) {
            log.info("获取到 xiaomiio ssecurity: {}... (micoapi: {}...)",
                    ssecurity.substring(0, Math.min(8, ssecurity.length())),
                    currentToken.ssecurity().substring(0, Math.min(8, currentToken.ssecurity().length())));
            this.ioSsecurity = ssecurity;
        }
        String clientSign = crypto.computeClientSign(nonce, ssecurity);
        return fetchServiceToken(resp.get("location").asText(), nonce, clientSign);
    }

    private LoginResult ensureTokenLoaded() {
        if (currentToken == null) {
            currentToken = tokenManager.loadActiveToken();
        }
        return currentToken;
    }

    private String accountBaseUrl() {
        return properties.api().baseUrl();
    }

    private JsonNode serviceLogin(String sid, String passToken) throws IOException {
        String url = accountBaseUrl() + ACCOUNT_PATH + "serviceLogin?sid=" + sid + "&_json=true";

        Request.Builder reqBuilder = new Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", OkHttpClientFactory.getUserAgentAccount());

        StringBuilder cookieBuilder = new StringBuilder();
        cookieBuilder.append("sdkVersion=3.9; deviceId=").append(deviceId);
        if (currentToken != null) {
            cookieBuilder.append("; userId=").append(currentToken.userId());
            if (currentToken.passToken() != null) {
                cookieBuilder.append("; passToken=").append(currentToken.passToken());
            }
        }
        reqBuilder.header("Cookie", cookieBuilder.toString());

        try (Response response = httpClient.newCall(reqBuilder.build()).execute()) {
            return parseXiaomiResponse(response);
        }
    }

    private JsonNode serviceLoginAuth2(Map<String, String> data) throws IOException {
        FormBody.Builder formBuilder = new FormBody.Builder();
        data.forEach(formBuilder::add);

        StringBuilder cookieBuilder = new StringBuilder();
        cookieBuilder.append("sdkVersion=3.9; deviceId=").append(deviceId);
        if (currentToken != null && currentToken.passToken() != null) {
            cookieBuilder.append("; userId=").append(currentToken.userId());
            cookieBuilder.append("; passToken=").append(currentToken.passToken());
        }

        Request request = new Request.Builder()
                .url(accountBaseUrl() + ACCOUNT_PATH + "serviceLoginAuth2")
                .post(formBuilder.build())
                .header("User-Agent", OkHttpClientFactory.getUserAgentAccount())
                .header("Cookie", cookieBuilder.toString())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return parseXiaomiResponse(response);
        }
    }

    private String fetchServiceToken(String location, String nonce, String clientSign) throws IOException {
        String url = location + "&clientSign=" + URLEncoder.encode(clientSign, StandardCharsets.UTF_8);

        StringBuilder cookieBuilder = new StringBuilder();
        cookieBuilder.append("sdkVersion=3.9; deviceId=").append(deviceId);
        if (currentToken != null && currentToken.passToken() != null) {
            cookieBuilder.append("; userId=").append(currentToken.userId());
            cookieBuilder.append("; passToken=").append(currentToken.passToken());
        }

        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", OkHttpClientFactory.getUserAgentAccount())
                .header("Cookie", cookieBuilder.toString())
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
            throw new XiaomiAuthException("AUTH_001", "登录失败，请重试");
        }
    }

    private JsonNode parseXiaomiResponse(Response response) throws IOException {
        String body = response.body() != null ? response.body().string() : "";
        if (body.startsWith("&&&START&&&")) {
            body = body.substring(11);
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new XiaomiApiException(response.code(), "API_002",
                    "Failed to parse response: " + body, e);
        }
    }

    private JsonNode parseResponse(Response response) throws IOException {
        String body = response.body() != null ? response.body().string() : "";
        try {
            JsonNode json = objectMapper.readTree(body);
            if (json.has("code")) {
                int code = json.get("code").asInt();
                if (code != 0) {
                    String message = json.has("message") ? json.get("message").asText() : "Unknown error";
                    if (message.toLowerCase().contains("auth")) {
                        throw new XiaomiAuthException("AUTH_004", message);
                    }
                    throw new XiaomiApiException(response.code(), "API_002", message);
                }
            }
            return json;
        } catch (JsonProcessingException e) {
            throw new XiaomiApiException(response.code(), "API_002",
                    "Failed to parse response", e);
        }
    }

    private void ensureLoggedIn() {
        if (getCurrentToken() == null) {
            throw new XiaomiAuthException("AUTH_004", "Not logged in");
        }
    }
}
