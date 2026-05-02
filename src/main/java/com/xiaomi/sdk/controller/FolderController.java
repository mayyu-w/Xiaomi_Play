package com.xiaomi.sdk.controller;

import com.xiaomi.sdk.config.XiaomiSdkProperties;
import com.xiaomi.sdk.entity.FolderConfigEntity;
import com.xiaomi.sdk.entity.PlayStateEntity;
import com.xiaomi.sdk.mapper.FolderConfigMapper;
import com.xiaomi.sdk.mapper.PlayStateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * 文件夹浏览控制器
 *
 * @author awen
 */
@RestController
@RequestMapping("/api/folder")
public class FolderController {

    private static final Logger log = LoggerFactory.getLogger(FolderController.class);

    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "mp3", "flac", "wav", "ape", "ogg", "m4a"
    );

    private final XiaomiSdkProperties properties;
    private final FolderConfigMapper configMapper;
    private final PlayStateMapper stateMapper;

    public FolderController(XiaomiSdkProperties properties, FolderConfigMapper configMapper,
                            PlayStateMapper stateMapper) {
        this.properties = properties;
        this.configMapper = configMapper;
        this.stateMapper = stateMapper;
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> config = loadConfig();
        return ResponseEntity.ok(Map.of("success", true, "data", config, "message", "ok"));
    }

    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkPath(@RequestParam String path) {
        if (path == null || path.isBlank()) {
            return ResponseEntity.ok(Map.of("success", false, "message", "路径不能为空"));
        }
        Path dir = Path.of(path).normalize();
        if (Files.isDirectory(dir)) {
            return ResponseEntity.ok(Map.of("success", true, "message", "目录存在"));
        }
        return ResponseEntity.ok(Map.of("success", false, "message", "目录不存在: " + dir));
    }

    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> saveConfig(@RequestBody Map<String, Object> body) {
        String path = (String) body.getOrDefault("path", "");
        boolean watchEnabled = Boolean.TRUE.equals(body.get("watchEnabled"));
        int watchInterval = body.get("watchInterval") instanceof Number n ? n.intValue() : 10;
        String ignoreDirs = (String) body.getOrDefault("ignoreDirs", "");
        int maxDepth = body.get("maxDepth") instanceof Number n ? n.intValue() : 10;
        String serverUrl = (String) body.getOrDefault("serverUrl", "");

        // 校验
        watchInterval = Math.max(1, Math.min(3600, watchInterval));
        maxDepth = Math.max(1, Math.min(50, maxDepth));

        FolderConfigEntity entity = configMapper.selectListByQuery(
                com.mybatisflex.core.query.QueryWrapper.create().limit(1)
        ).stream().findFirst().orElse(null);
        boolean isNew = (entity == null);
        if (isNew) {
            entity = new FolderConfigEntity();
            entity.setCreatedAt(OffsetDateTime.now());
            entity.setId(1L);
        }
        entity.setPath(path);
        entity.setWatchEnabled(watchEnabled);
        entity.setWatchInterval(watchInterval);
        entity.setIgnoreDirs(ignoreDirs);
        entity.setMaxDepth(maxDepth);
        entity.setServerUrl(serverUrl);
        entity.setUpdatedAt(OffsetDateTime.now());

        int rows = isNew ? configMapper.insert(entity) : configMapper.update(entity);
        log.info("文件夹配置已{}: path={}, rows={}", isNew ? "创建" : "更新", path, rows);
        return ResponseEntity.ok(Map.of("success", true, "message", "保存成功"));
    }

    @GetMapping("/audio/{*relPath}")
    public ResponseEntity<org.springframework.core.io.Resource> streamAudio(
            @PathVariable String relPath,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {
        Map<String, Object> config = loadConfig();
        String rootPath = (String) config.get("path");
        if (rootPath == null || rootPath.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String clean = relPath.startsWith("/") ? relPath.substring(1) : relPath;
        Path path = Path.of(rootPath).resolve(clean.replace('/', '\\')).normalize();
        log.debug("音频流请求: relPath={}, fullPath={}, exists={}", clean, path, Files.exists(path));

        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
        }

        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || !AUDIO_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            String contentType = probeContentType(name);
            org.springframework.core.io.Resource resource = new org.springframework.core.io.FileSystemResource(path);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(Files.size(path))
                    .header("Accept-Ranges", "bytes")
                    .body(resource);
        } catch (Exception e) {
            log.error("流式传输失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/scan")
    public ResponseEntity<Map<String, Object>> scan(@RequestParam(required = false) String path) {
        Map<String, Object> config = loadConfig();
        String scanPath = (path != null && !path.isBlank()) ? path : (String) config.get("path");
        if (scanPath == null || scanPath.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", "未配置音频文件夹路径"));
        }

        Path dir = Path.of(scanPath).normalize();
        if (!Files.isDirectory(dir)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", "路径不存在或不是文件夹: " + dir));
        }

        int maxDepth = (int) config.get("maxDepth");
        Set<String> ignoreSet = parseIgnoreDirs((String) config.get("ignoreDirs"));

        try {
            List<Map<String, Object>> folders = new ArrayList<>();
            int totalCount = 0;

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path entry : stream) {
                    if (!Files.isDirectory(entry)) continue;
                    String name = entry.getFileName().toString();
                    if (ignoreSet.contains(name)) continue;
                    int count = countAudioFiles(entry, maxDepth, ignoreSet);
                    totalCount += count;
                    folders.add(Map.of(
                            "name", name,
                            "path", entry.toString(),
                            "count", count
                    ));
                }
            }

            folders.sort(Comparator.comparing(m -> (String) m.get("name")));

            List<Map<String, Object>> result = new ArrayList<>();
            result.add(Map.of("name", "全部", "path", dir.toString(), "count", totalCount));
            result.addAll(folders);

            return ResponseEntity.ok(Map.of("success", true, "data", result, "message", "ok"));
        } catch (Exception e) {
            log.error("扫描文件夹失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false, "message", "扫描失败: " + e.getMessage()));
        }
    }

    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> listFiles(@RequestParam String path) {
        if (path == null || path.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "路径不能为空"));
        }
        Path dir = Path.of(path).normalize();
        if (!Files.isDirectory(dir)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "路径不存在"));
        }

        Map<String, Object> config = loadConfig();
        int maxDepth = (int) config.get("maxDepth");
        Set<String> ignoreSet = parseIgnoreDirs((String) config.get("ignoreDirs"));
        Path root = Path.of((String) config.get("path")).normalize();

        try {
            List<Map<String, String>> files = new ArrayList<>();
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                int depth = 0;

                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                    if (depth >= maxDepth) return FileVisitResult.SKIP_SUBTREE;
                    if (depth > 0 && ignoreSet.contains(d.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    depth++;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) {
                    depth--;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String name = file.getFileName().toString();
                    int dot = name.lastIndexOf('.');
                    if (dot > 0) {
                        String ext = name.substring(dot + 1).toLowerCase();
                        if (AUDIO_EXTENSIONS.contains(ext)) {
                            String rel = root.relativize(file).toString().replace('\\', '/');
                            files.add(Map.of("name", name, "path", file.toString(), "urlPath", rel));
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
            });
            files.sort(Comparator.comparing(m -> m.get("name")));
            return ResponseEntity.ok(Map.of("success", true, "data", files, "message", "ok"));
        } catch (Exception e) {
            log.error("列出文件失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false, "message", "列出文件失败: " + e.getMessage()));
        }
    }

    @PostMapping("/history")
    public ResponseEntity<Map<String, Object>> recordHistory(@RequestBody Map<String, String> body) {
        String folderPath = body.getOrDefault("folderPath", "");
        String fileName = body.getOrDefault("fileName", "");
        String filePath = body.getOrDefault("filePath", "");
        String urlPath = body.getOrDefault("urlPath", "");

        PlayStateEntity entity = new PlayStateEntity();
        entity.setId(1L);
        entity.setFolderPath(folderPath);
        entity.setFileName(fileName);
        entity.setFilePath(filePath);
        entity.setUrlPath(urlPath);
        entity.setUpdatedAt(OffsetDateTime.now());

        if (stateMapper.selectOneById(1L) != null) {
            stateMapper.update(entity);
        } else {
            stateMapper.insert(entity);
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "ok"));
    }

    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getHistory() {
        PlayStateEntity state = stateMapper.selectOneById(1L);
        if (state != null) {
            return ResponseEntity.ok(Map.of("success", true, "data", Map.of(
                    "folderPath", state.getFolderPath() != null ? state.getFolderPath() : "",
                    "fileName", state.getFileName() != null ? state.getFileName() : "",
                    "filePath", state.getFilePath() != null ? state.getFilePath() : "",
                    "urlPath", state.getUrlPath() != null ? state.getUrlPath() : ""
            ), "message", "ok"));
        }
        return ResponseEntity.ok(Map.of("success", true, "data", null, "message", "no history"));
    }

    private Map<String, Object> loadConfig() {
        FolderConfigEntity entity = configMapper.selectListByQuery(
                com.mybatisflex.core.query.QueryWrapper.create().limit(1)
        ).stream().findFirst().orElse(null);

        XiaomiSdkProperties.Folder defaults = properties.folder();

        if (entity != null) {
            return Map.of(
                    "path", entity.getPath() != null ? entity.getPath() : "",
                    "watchEnabled", entity.getWatchEnabled() != null ? entity.getWatchEnabled() : false,
                    "watchInterval", entity.getWatchInterval() != null ? entity.getWatchInterval() : defaults.watchInterval(),
                    "ignoreDirs", entity.getIgnoreDirs() != null ? entity.getIgnoreDirs() : "",
                    "maxDepth", entity.getMaxDepth() != null ? entity.getMaxDepth() : defaults.maxDepth(),
                    "serverUrl", entity.getServerUrl() != null ? entity.getServerUrl() : "",
                    "playMode", entity.getPlayMode() != null ? entity.getPlayMode() : 4
            );
        }
        return Map.of(
                "path", defaults.path() != null ? defaults.path() : "",
                "watchEnabled", defaults.watchEnabled(),
                "watchInterval", defaults.watchInterval(),
                "ignoreDirs", defaults.ignoreDirs() != null ? defaults.ignoreDirs() : "",
                "maxDepth", defaults.maxDepth(),
                "serverUrl", "",
                "playMode", 4
        );
    }

    private Set<String> parseIgnoreDirs(String raw) {
        Set<String> set = new HashSet<>();
        if (raw != null && !raw.isBlank()) {
            for (String d : raw.split(",")) {
                String trimmed = d.trim();
                if (!trimmed.isEmpty()) set.add(trimmed);
            }
        }
        return set;
    }

    private int countAudioFiles(Path dir, int maxDepth, Set<String> ignoreSet) throws IOException {
        int[] count = {0};
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            int depth = 0;

            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                if (depth >= maxDepth) return FileVisitResult.SKIP_SUBTREE;
                if (depth > 0 && ignoreSet.contains(d.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                depth++;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) {
                depth--;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString();
                int dot = name.lastIndexOf('.');
                if (dot > 0) {
                    String ext = name.substring(dot + 1).toLowerCase();
                    if (AUDIO_EXTENSIONS.contains(ext)) {
                        count[0]++;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.SKIP_SUBTREE;
            }
        });
        return count[0];
    }

    private String probeContentType(String name) {
        if (name.endsWith(".mp3")) return "audio/mpeg";
        if (name.endsWith(".flac")) return "audio/flac";
        if (name.endsWith(".wav")) return "audio/wav";
        if (name.endsWith(".ogg")) return "audio/ogg";
        if (name.endsWith(".m4a")) return "audio/mp4";
        if (name.endsWith(".ape")) return "audio/x-ape";
        return "application/octet-stream";
    }
}
