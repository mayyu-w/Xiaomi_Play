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
import okhttp3.*;

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

    private static final String ACCOUNT_PATH = "/pass/";
    private static final String SID_XIAOMIIO = "xiaomiio";
    private static final String SID_MICOAPI = "micoapi";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CryptoService crypto;
    private final XiaomiSdkProperties properties;

    private String deviceId;
    private LoginResult currentToken;

    public MiAccountService(OkHttpClient httpClient, ObjectMapper objectMapper,
                            CryptoService crypto, XiaomiSdkProperties properties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.crypto = crypto;
        this.properties = properties;
        this.deviceId = crypto.getRandomString(16).toUpperCase();
    }

    /**
     * 小米账号登录
     */
    public LoginResult login(String username, String password) {
        try {
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
                authData.put("user", crypto.encodeUsername(username));
                authData.put("hash", hash);

                loginResp = serviceLoginAuth2(authData);
                if (loginResp.has("code") && loginResp.get("code").asInt() != 0) {
                    throw new XiaomiAuthException("AUTH_001", "Login failed: " + loginResp);
                }
            }

            String userId = loginResp.get("userId").asText();
            String passToken = loginResp.get("passToken").asText();
            String ssecurity = loginResp.get("ssecurity").asText();
            String nonce = loginResp.get("nonce").asText();
            String location = loginResp.get("location").asText();

            // Step 3: securityTokenService — 获取 serviceToken
            String clientSign = crypto.computeClientSign(nonce, ssecurity);
            String serviceToken = fetchServiceToken(location, nonce, clientSign);

            this.currentToken = new LoginResult(
                    userId, passToken, ssecurity, serviceToken,
                    System.currentTimeMillis() + 30L * 24 * 3600 * 1000,
                    null, 0
            );
            return this.currentToken;

        } catch (XiaomiAuthException e) {
            throw e;
        } catch (Exception e) {
            this.currentToken = null;
            throw new XiaomiAuthException("AUTH_001", "Login failed", e);
        }
    }

    /**
     * 刷新 Token
     */
    public LoginResult refreshToken() {
        if (currentToken == null) {
            throw new XiaomiAuthException("AUTH_004", "No token to refresh");
        }
        // 重新登录获取新 token
        return login(currentToken.userId(), currentToken.passToken());
    }

    /**
     * 登出
     */
    public void logout() {
        this.currentToken = null;
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

    /**
     * 获取当前 Token
     */
    public LoginResult getCurrentToken() {
        return currentToken;
    }

    public void setCurrentToken(LoginResult token) {
        this.currentToken = token;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    /**
     * 构建指定 sid 的 Cookie 字符串
     */
    public String buildCookieString(String sid) {
        if (currentToken == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("sdkVersion=3.9; ");
        sb.append("deviceId=").append(deviceId).append("; ");
        sb.append("userId=").append(currentToken.userId()).append("; ");
        sb.append("serviceToken=").append(currentToken.serviceToken()).append("; ");
        return sb.toString();
    }

    // ---- 内部方法 ----

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
            // serviceToken 从 Set-Cookie 中获取
            for (String header : response.headers("Set-Cookie")) {
                if (header.startsWith("serviceToken=")) {
                    int end = header.indexOf(';', "serviceToken=".length());
                    return end > 0
                            ? header.substring("serviceToken=".length(), end)
                            : header.substring("serviceToken=".length());
                }
            }
            throw new XiaomiAuthException("AUTH_001", "serviceToken not found in response cookies");
        }
    }

    private JsonNode parseXiaomiResponse(Response response) throws IOException {
        String body = response.body() != null ? response.body().string() : "";
        // 小米 API 响应前缀 "&&&START&&&"
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
        if (currentToken == null) {
            throw new XiaomiAuthException("AUTH_004", "Not logged in");
        }
    }
}
