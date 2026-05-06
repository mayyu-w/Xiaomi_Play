package com.xiaomi.sdk.music;

import com.xiaomi.sdk.entity.FolderConfigEntity;
import com.xiaomi.sdk.entity.PlayStateEntity;
import com.xiaomi.sdk.mapper.FolderConfigMapper;
import com.xiaomi.sdk.mapper.PlayStateMapper;
import com.xiaomi.sdk.model.PlayerStatus;
import com.xiaomi.sdk.mina.MiNAService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * 自动切歌管理器 — 后端播放模式实现
 * 0=单曲循环 1=全部循环 2=随机播放 3=单曲播放 4=顺序播放
 * @author awen
 */
public class AutoPlayManager {

    private static final Logger log = LoggerFactory.getLogger(AutoPlayManager.class);
    private static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3", "flac", "wav", "ape", "ogg", "m4a");

    private final MiNAService minaService;
    private final PlayStateMapper playStateMapper;
    private final FolderConfigMapper folderConfigMapper;

    private static final long COOLDOWN_MS = 5000;

    private volatile boolean enabled;
    private volatile int playMode;
    private volatile String currentUrlPath;
    private volatile List<String> folderFiles;
    private volatile String deviceId;
    private volatile long lastSwitchTime;

    public AutoPlayManager(MiNAService minaService, PlayStateMapper playStateMapper,
                           FolderConfigMapper folderConfigMapper) {
        this.minaService = minaService;
        this.playStateMapper = playStateMapper;
        this.folderConfigMapper = folderConfigMapper;
    }

    public void enable(String deviceId, int playMode) {
        this.deviceId = deviceId;
        this.playMode = playMode;
        this.enabled = true;
        PlayStateEntity state = playStateMapper.selectOneById(1L);
        if (state != null && state.getUrlPath() != null) {
            this.currentUrlPath = state.getUrlPath();
            this.folderFiles = scanFolder();
        }
        log.info("自动切歌已启用: deviceId={}, mode={}, currentFile={}", deviceId, playMode, currentUrlPath);
    }

    public void disable() {
        this.enabled = false;
        log.info("自动切歌已停用");
    }

    public void reEnable(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) return;
        this.deviceId = deviceId;

        PlayStateEntity state = playStateMapper.selectOneById(1L);
        if (state != null && state.getUrlPath() != null) {
            this.currentUrlPath = state.getUrlPath();
            this.folderFiles = scanFolder();
        }

        FolderConfigEntity config = folderConfigMapper.selectOneById(1L);
        if (config != null && config.getPlayMode() != null) {
            this.playMode = config.getPlayMode();
        }

        if (this.folderFiles != null && !this.folderFiles.isEmpty()) {
            this.enabled = true;
            log.info("前端已断开，后端自动切歌已恢复: deviceId={}, mode={}, currentFile={}", this.deviceId, playMode, currentUrlPath);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getCurrentUrlPath() {
        return currentUrlPath;
    }

    public boolean checkAndNext(PlayerStatus prev, PlayerStatus curr) {
        if (!enabled || folderFiles == null || folderFiles.isEmpty()) return false;
        if (System.currentTimeMillis() - lastSwitchTime < COOLDOWN_MS) return false;

        boolean songEnded = false;
        // 主动检测：播放进度接近结尾，在设备自动循环前切歌
        if (curr.status() == 1 && curr.duration() > 0 && curr.playTime() >= curr.duration() ) {
            songEnded = true;
        }
        // 播放中 → 停止/暂停，且 position 归零（歌曲播完）
        if (!songEnded && prev != null && prev.status() == 1 && (curr.status() == 0 || curr.status() == 2)
                && prev.playTime() > 0 && curr.playTime() == 0) {
            songEnded = true;
        }
        // 回跳兜底：position 从接近结尾跳回接近 0（设备自动循环）
        if (!songEnded && prev != null && curr.status() == 1 && prev.status() == 1
                && prev.duration() > 0 && prev.playTime() > prev.duration() * 0.8
                && curr.playTime() < 3 && curr.playTime() < prev.playTime() - 5) {
            songEnded = true;
        }

        if (!songEnded) return false;

        String nextUrlPath = findNext();
        if (nextUrlPath == null) {
            log.info("自动切歌: 无下一首，停止");
            enabled = false;
            return false;
        }

        String audioUrl = buildAudioUrl(nextUrlPath);
        log.info("自动切歌: {} → {}", currentUrlPath, nextUrlPath);
        lastSwitchTime = System.currentTimeMillis();
        minaService.playerStop(deviceId);
        minaService.playByUrl(deviceId, audioUrl);
        currentUrlPath = nextUrlPath;

        PlayStateEntity state = new PlayStateEntity();
        state.setId(1L);
        state.setUrlPath(nextUrlPath);
        state.setFileName(nextUrlPath.contains("/") ? nextUrlPath.substring(nextUrlPath.lastIndexOf('/') + 1) : nextUrlPath);
        playStateMapper.update(state);
        return true;
    }

    private String findNext() {
        if (folderFiles == null || folderFiles.isEmpty()) return null;
        int idx = folderFiles.indexOf(currentUrlPath);
        if (idx < 0) idx = 0;

        return switch (playMode) {
            case 0 -> folderFiles.get(idx); // 单曲循环
            case 1 -> folderFiles.get((idx + 1) % folderFiles.size()); // 全部循环
            case 2 -> { // 随机
                if (folderFiles.size() == 1) yield folderFiles.get(0);
                int r;
                do { r = new Random().nextInt(folderFiles.size()); } while (r == idx);
                yield folderFiles.get(r);
            }
            case 3 -> null; // 单曲播放，播完停止
            case 4 -> (idx < folderFiles.size() - 1) ? folderFiles.get(idx + 1) : null; // 顺序
            default -> null;
        };
    }

    private List<String> scanFolder() {
        PlayStateEntity state = playStateMapper.selectOneById(1L);
        if (state == null || state.getFolderPath() == null) return List.of();
        Path folder = Path.of(state.getFolderPath()).normalize();
        if (!Files.isDirectory(folder)) return List.of();

        FolderConfigEntity config = folderConfigMapper.selectOneById(1L);
        Path root = (config != null && config.getPath() != null)
                ? Path.of(config.getPath()).normalize() : folder;

        List<String> files = new ArrayList<>();
        try (var stream = Files.list(folder)) {
            stream.filter(Files::isRegularFile)
                    .filter(f -> {
                        String name = f.getFileName().toString();
                        int dot = name.lastIndexOf('.');
                        return dot > 0 && AUDIO_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase());
                    })
                    .forEach(f -> files.add(root.relativize(f).toString().replace('\\', '/')));
        } catch (IOException e) {
            log.error("扫描文件夹失败: {}", e.getMessage());
        }
        // 按文件名排序，与前端 listFiles 一致
        files.sort((a, b) -> {
            String na = a.contains("/") ? a.substring(a.lastIndexOf('/') + 1) : a;
            String nb = b.contains("/") ? b.substring(b.lastIndexOf('/') + 1) : b;
            return na.compareTo(nb);
        });
        log.info("扫描当前文件夹: folder={}, 文件数={}", folder.getFileName(), files.size());
        return files;
    }

    private String buildAudioUrl(String urlPath) {
        FolderConfigEntity config = folderConfigMapper.selectOneById(1L);
        String base = (config != null && config.getServerUrl() != null && !config.getServerUrl().isEmpty())
                ? config.getServerUrl() : "http://localhost:8080";
        try {
            String[] parts = urlPath.split("/");
            String[] encoded = new String[parts.length];
            for (int i = 0; i < parts.length; i++) {
                encoded[i] = java.net.URLEncoder.encode(parts[i], "UTF-8").replace("+", "%20");
            }
            if (!base.endsWith("/")) base += "/";
            return base + "api/folder/audio/" + String.join("/", encoded);
        } catch (Exception e) {
            throw new RuntimeException("构建音频URL失败", e);
        }
    }

    private Set<String> parseIgnoreDirs(String ignoreDirs) {
        if (ignoreDirs == null || ignoreDirs.isEmpty()) return Set.of();
        Set<String> set = new HashSet<>();
        for (String s : ignoreDirs.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) set.add(trimmed);
        }
        return set;
    }
}
