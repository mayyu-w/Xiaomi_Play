package com.xiaomi.sdk.miot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomi.sdk.account.MiAccountService;
import com.xiaomi.sdk.config.XiaomiSdkProperties;
import com.xiaomi.sdk.crypto.CryptoService;
import com.xiaomi.sdk.exception.XiaomiApiException;
import com.xiaomi.sdk.http.OkHttpClientFactory;
import com.xiaomi.sdk.model.Device;
import com.xiaomi.sdk.model.MiIOResponse;
import com.xiaomi.sdk.model.PropertyValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import okhttp3.*;

import java.io.IOException;

import java.util.*;

/**
 * MIoT 协议设备控制服务
 * 实现 MIoT 属性读写、动作执行
 * @author awen
 */
public class MiIOService {

    private static final Logger log = LoggerFactory.getLogger(MiIOService.class);
    private static final String SID = "xiaomiio";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CryptoService crypto;
    private final MiAccountService accountService;
    private final XiaomiSdkProperties properties;

    public MiIOService(OkHttpClient httpClient, ObjectMapper objectMapper,
                       CryptoService crypto, MiAccountService accountService,
                       XiaomiSdkProperties properties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.crypto = crypto;
        this.accountService = accountService;
        this.properties = properties;
    }

    /**
     * 读取设备属性
     */
    public List<PropertyValue> getProperties(String did, int[] siids, int[] piids) {
        List<Map<String, Object>> params = new ArrayList<>();
        for (int i = 0; i < siids.length; i++) {
            params.add(Map.of("did", did, "siid", siids[i], "piid", piids[i]));
        }
        JsonNode result = miotRequest("prop/get", params);
        List<PropertyValue> values = new ArrayList<>();
        if (result.isArray()) {
            for (JsonNode item : result) {
                values.add(new PropertyValue(
                        item.path("did").asText(),
                        item.path("siid").asInt(),
                        item.path("piid").asInt(),
                        parseValue(item.path("value")),
                        item.path("code").asInt()
                ));
            }
        }
        return values;
    }

    /**
     * 写入设备属性
     */
    public MiIOResponse setProperty(String did, int siid, int piid, Object value) {
        List<Map<String, Object>> params = List.of(
                Map.of("did", did, "siid", siid, "piid", piid, "value", value)
        );
        JsonNode result = miotRequest("prop/set", params);
        if (result.isArray() && !result.isEmpty()) {
            return new MiIOResponse(result.get(0).path("code").asInt(), result.get(0));
        }
        return new MiIOResponse(-1, result);
    }

    /**
     * 执行设备动作
     */
    public MiIOResponse executeAction(String did, int siid, int aiid, List<Object> args) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("did", did);
        params.put("siid", siid);
        params.put("aiid", aiid);
        params.put("in", args != null ? args : Collections.emptyList());
        JsonNode result = miotRequest("action", params);
        return new MiIOResponse(result.path("code").asInt(-1), result);
    }

    /**
     * 获取设备列表
     */
    public List<Device> deviceList() {
        Map<String, Object> data = Map.of(
                "getVirtualModel", false,
                "getHuamiDevices", 0
        );
        JsonNode result = miioRequest("/home/device_list", data);
        List<Device> devices = new ArrayList<>();
        JsonNode list = result.path("list");
        if (list.isArray()) {
            for (JsonNode item : list) {
                devices.add(new Device(
                        item.path("did").asText(),
                        item.path("name").asText(),
                        item.path("model").asText(),
                        item.path("token").asText(""),
                        "",
                        ""
                ));
            }
        }
        return devices;
    }

    // ---- 内部方法 ----

    private JsonNode miotRequest(String cmd, Object params) {
        Map<String, Object> data = Map.of("params", params);
        return miioRequest("/miotspec/" + cmd, data);
    }

    private JsonNode miioRequest(String uri, Object data) {
        ensureLoggedIn();
        try {
            return doMiioRequest(uri, data);
        } catch (XiaomiApiException e) {
            if (e.getHttpStatus() == 401) {
                log.info("MIoT 请求 401，刷新 Token 重试: uri={}", uri);
                accountService.refreshToken();
                return doMiioRequest(uri, data);
            }
            throw e;
        }
    }

    private JsonNode doMiioRequest(String uri, Object data) {
        try {
            String jsonData = objectMapper.writeValueAsString(data);
            String ssecurity = accountService.getSsecurityForMiot();
            Map<String, String> signedData = crypto.signData(uri, jsonData, ssecurity);

            String url = properties.api().ioUrl() + uri;

            log.info("MIoT签名 uri={}, ssecurity={}..., nonce={}, data={}",
                    uri,
                    ssecurity != null && ssecurity.length() > 8 ? ssecurity.substring(0, 8) + "..." : ssecurity,
                    signedData.get("_nonce"),
                    jsonData);
            RequestBody body = new FormBody.Builder()
                    .add("_nonce", signedData.get("_nonce"))
                    .add("data", signedData.get("data"))
                    .add("signature", signedData.get("signature"))
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .header("User-Agent", OkHttpClientFactory.getUserAgentMio())
                    .header("x-xiaomi-protocal-flag-cli", "PROTOCAL-HTTP2")
                    .header("Cookie", accountService.buildCookieString(SID) +
                            "; PassportDeviceId=" + accountService.getDeviceId())
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "{}";
                log.info("MIoT响应 {} status={} body={}", uri, response.code(),
                        responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);
                JsonNode json = objectMapper.readTree(responseBody);

                if (json.has("code")) {
                    int code = json.get("code").asInt();
                    if (code == 0) {
                        return json.path("result");
                    }
                    if (json.path("message").asText("").toLowerCase().contains("auth")) {
                        throw new XiaomiApiException(401, "AUTH_004", "Auth error");
                    }
                    throw new XiaomiApiException(response.code(), "API_002",
                            "MIoT error: code=" + code);
                }
                return json;
            }
        } catch (XiaomiApiException e) {
            throw e;
        } catch (Exception e) {
            throw new XiaomiApiException(500, "API_002", "MIoT request failed", e);
        }
    }

    private Object parseValue(JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull()) return null;
        if (valueNode.isBoolean()) return valueNode.asBoolean();
        if (valueNode.isInt()) return valueNode.asInt();
        if (valueNode.isLong()) return valueNode.asLong();
        if (valueNode.isDouble()) return valueNode.asDouble();
        return valueNode.asText();
    }

    private void ensureLoggedIn() {
        if (accountService.getCurrentToken() == null) {
            throw new XiaomiApiException(401, "AUTH_004", "Not logged in");
        }
    }
}
