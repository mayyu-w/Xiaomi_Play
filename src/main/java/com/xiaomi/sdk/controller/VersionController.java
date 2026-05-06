package com.xiaomi.sdk.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 版本信息控制器
 * @author awen
 */
@RestController
@RequestMapping("/api/about")
public class VersionController {

    private static final Logger log = LoggerFactory.getLogger(VersionController.class);
    private static final String GITHUB_REPO = "mayyu-w/Xiaomi_Play";
    private static final String GITHUB_RELEASES_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";

    private final ObjectMapper objectMapper;

    private String cachedVersion;

    public VersionController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        loadVersionInfo();
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getVersion() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", cachedVersion != null ? cachedVersion : "unknown");
        data.put("repository", "https://github.com/" + GITHUB_REPO);
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    @GetMapping("/check-update")
    public ResponseEntity<Map<String, Object>> checkUpdate() {
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build();

            Request request = new Request.Builder()
                    .url(GITHUB_RELEASES_URL)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "XiaomiPlay-SDK")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return ResponseEntity.ok(Map.of("success", false, "message", "无法连接 GitHub，请稍后重试"));
                }

                String body = response.body().string();
                JsonNode json = objectMapper.readTree(body);

                String latestVersion = json.path("tag_name").asText("");
                String htmlUrl = json.path("html_url").asText("");
                String releaseNotes = json.path("body").asText("");
                String publishedAt = json.path("published_at").asText("");

                if (latestVersion.isEmpty()) {
                    return ResponseEntity.ok(Map.of("success", false, "message", "未找到发布版本"));
                }

                // tag_name 通常带 v 前缀，比较时去掉
                String currentVer = cachedVersion != null ? cachedVersion : "0.0.0";
                String latestVer = latestVersion.startsWith("v") ? latestVersion.substring(1) : latestVersion;
                boolean hasUpdate = compareVersions(latestVer, currentVer) > 0;

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("hasUpdate", hasUpdate);
                data.put("currentVersion", currentVer);
                data.put("latestVersion", latestVer);
                data.put("latestTag", latestVersion);
                data.put("releaseUrl", htmlUrl);
                data.put("releaseNotes", releaseNotes);
                data.put("publishedAt", publishedAt);

                return ResponseEntity.ok(Map.of("success", true, "data", data));
            }
        } catch (Exception e) {
            log.warn("检查更新失败: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("success", false, "message", "检查更新失败：" + e.getMessage()));
        }
    }

    /**
     * 简单的语义化版本比较，返回正数表示 v1 > v2
     */
    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int p1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int p2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            if (p1 != p2) return p1 - p2;
        }
        return 0;
    }

    private int parseVersionPart(String part) {
        // 处理类似 "0-SNAPSHOT" 的情况
        String num = part.split("-")[0].split("\\+")[0];
        try {
            return Integer.parseInt(num);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void loadVersionInfo() {
        try {
            ClassPathResource resource = new ClassPathResource("version.properties");
            try (InputStream is = resource.getInputStream()) {
                Properties props = new Properties();
                props.load(is);
                cachedVersion = props.getProperty("version", "unknown");
            }
        } catch (Exception e) {
            log.warn("加载版本信息失败: {}", e.getMessage());
            cachedVersion = "unknown";
        }
    }
}
