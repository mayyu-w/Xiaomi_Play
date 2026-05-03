package com.xiaomi.sdk.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mybatisflex.core.query.QueryWrapper;
import com.xiaomi.sdk.entity.FolderConfigEntity;
import com.xiaomi.sdk.entity.PlayStateEntity;
import com.xiaomi.sdk.entity.ScheduledTaskEntity;
import com.xiaomi.sdk.entity.ScheduledTaskLogEntity;
import com.xiaomi.sdk.mapper.FolderConfigMapper;
import com.xiaomi.sdk.mapper.PlayStateMapper;
import com.xiaomi.sdk.mapper.ScheduledTaskLogMapper;
import com.xiaomi.sdk.mapper.ScheduledTaskMapper;
import com.xiaomi.sdk.mina.MiNAService;
import com.xiaomi.sdk.miot.MiIOService;
import com.xiaomi.sdk.music.AutoPlayManager;
import com.xiaomi.sdk.music.MusicService;
import com.xiaomi.sdk.music.PlayerStatusScheduler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 动态 Cron 任务管理器
 * @author awen
 */
public class CronTaskManager {

    private static final Logger log = LoggerFactory.getLogger(CronTaskManager.class);
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public static final Map<String, String> COMMAND_LIST = Map.ofEntries(
            Map.entry("play_next", "下一首"),
            Map.entry("play_prev", "上一首"),
            Map.entry("pause", "暂停"),
            Map.entry("resume", "继续播放"),
            Map.entry("set_volume", "设置音量"),
            Map.entry("set_play_mode", "设置播放模式"),
            Map.entry("play_last", "恢复上次播放"),
            Map.entry("tts", "TTS语音播报")
    );

    public static final Map<Integer, String> PLAY_MODE_MAP = Map.of(
            0, "单曲循环",
            1, "全部循环",
            2, "当前随机",
            3, "单曲播放",
            4, "顺序播放"
    );

    private final ScheduledTaskMapper taskMapper;
    private final ScheduledTaskLogMapper logMapper;
    private final PlayStateMapper playStateMapper;
    private final FolderConfigMapper folderConfigMapper;
    private final MiNAService minaService;
    private final MiIOService miioService;
    private final MusicService musicService;
    private final PlayerStatusScheduler statusScheduler;

    private final TaskScheduler scheduler;
    private final Map<Long, ScheduledFuture<?>> taskMap = new ConcurrentHashMap<>();

    public CronTaskManager(ScheduledTaskMapper taskMapper,
                           ScheduledTaskLogMapper logMapper,
                           PlayStateMapper playStateMapper,
                           FolderConfigMapper folderConfigMapper,
                           MiNAService minaService,
                           MiIOService miioService,
                           MusicService musicService,
                           PlayerStatusScheduler statusScheduler) {
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
        this.playStateMapper = playStateMapper;
        this.folderConfigMapper = folderConfigMapper;
        this.minaService = minaService;
        this.miioService = miioService;
        this.musicService = musicService;
        this.statusScheduler = statusScheduler;

        ThreadPoolTaskScheduler ts = new ThreadPoolTaskScheduler();
        ts.setPoolSize(4);
        ts.setThreadNamePrefix("cron-task-");
        ts.initialize();
        this.scheduler = ts;
    }

    @PostConstruct
    public void init() {
        loadAndScheduleAll();
    }

    public void loadAndScheduleAll() {
        List<ScheduledTaskEntity> tasks = taskMapper.selectListByQuery(
                QueryWrapper.create().where("enabled = true"));
        for (ScheduledTaskEntity task : tasks) {
            scheduleTask(task);
        }
        log.info("已加载 {} 个定时任务", tasks.size());
    }

    public void scheduleTask(ScheduledTaskEntity task) {
        cancelTask(task.getId());
        try {
            CronTrigger trigger = new CronTrigger(task.getCronExpr());
            ScheduledFuture<?> future = scheduler.schedule(
                    () -> executeTask(task), trigger);
            taskMap.put(task.getId(), future);
            log.info("定时任务已注册: id={}, name={}, cron={}", task.getId(), task.getTaskName(), task.getCronExpr());
        } catch (Exception e) {
            log.error("注册定时任务失败: id={}, cron={}, error={}", task.getId(), task.getCronExpr(), e.getMessage());
        }
    }

    public void cancelTask(Long taskId) {
        ScheduledFuture<?> future = taskMap.remove(taskId);
        if (future != null) {
            future.cancel(false);
            log.info("定时任务已取消: id={}", taskId);
        }
    }

    private void executeTask(ScheduledTaskEntity task) {
        String msg = "ok";
        boolean success = true;
        try {
            log.info("执行定时任务: id={}, name={}, command={}", task.getId(), task.getTaskName(), task.getCommand());
            List<Map<String, Object>> commands = resolveCommands(task);
            Integer taskPlayMode = null;
            boolean hasPlayLast = false;
            for (int i = 0; i < commands.size(); i++) {
                if (i > 0) {
                    Thread.sleep(1500);
                }
                Map<String, Object> cmdObj = commands.get(i);
                String cmd = (String) cmdObj.get("cmd");
                Object p = cmdObj.get("params");
                String params = (p instanceof Map && !((Map<?, ?>) p).isEmpty())
                        ? objectMapper.writeValueAsString(p) : null;
                if ("set_play_mode".equals(cmd)) {
                    taskPlayMode = parseParamInt(params, "mode", 2);
                }
                if ("play_last".equals(cmd)) {
                    hasPlayLast = true;
                }
                executeCommand(task.getDeviceId(), cmd, params);
            }
            // playByMusicUrl REPLACE_ALL 会重置播放模式，需要重新设置
            if (hasPlayLast) {
                int mode = (taskPlayMode != null) ? taskPlayMode : getFolderPlayMode();
                minaService.playerSetLoop(task.getDeviceId(), mode);
                log.info("play_last 后重新设置播放模式: mode={}--{}", mode, PLAY_MODE_MAP.get(mode));
                // 启用自动切歌
                AutoPlayManager autoPlay = statusScheduler.getAutoPlayManager();
                if (autoPlay != null) {
                    autoPlay.enable(task.getDeviceId(), mode);
                }
                // 启动播放状态轮询（前端未连接 SSE 时也需要轮询以检测歌曲结尾）
                statusScheduler.start(task.getDeviceId(), statusScheduler.getIntervalSeconds());
            }
        } catch (Exception e) {
            success = false;
            msg = e.getMessage();
            log.error("定时任务执行失败: id={}, error={}", task.getId(), e.getMessage());
        }
        logExecution(task, success, msg);
    }

    private int getFolderPlayMode() {
        try {
            FolderConfigEntity config = folderConfigMapper.selectOneById(1L);
            return (config != null && config.getPlayMode() != null) ? config.getPlayMode() : 2;
        } catch (Exception e) {
            return 2;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resolveCommands(ScheduledTaskEntity task) {
        String raw = task.getCommand();
        try {
            List<Map<String, Object>> list = objectMapper.readValue(raw, List.class);
            if (!list.isEmpty() && list.get(0) instanceof Map) {
                return list;
            }
        } catch (Exception ignored) {}
        // backward compat: single command
        Map<String, Object> single = new java.util.LinkedHashMap<>();
        single.put("cmd", raw);
        if (task.getParams() != null && !task.getParams().isEmpty()) {
            try {
                single.put("params", objectMapper.readValue(task.getParams(), Map.class));
            } catch (Exception ignored) {}
        }
        return List.of(single);
    }

    private String getCommandSummary(ScheduledTaskEntity task) {
        List<Map<String, Object>> commands = resolveCommands(task);
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> cmd : commands) {
            if (sb.length() > 0) sb.append(" → ");
            String cmdName = (String) cmd.get("cmd");
            sb.append(COMMAND_LIST.getOrDefault(cmdName, cmdName));
        }
        return sb.toString();
    }

    private void executeCommand(String deviceId, String command, String params) {
        switch (command) {
            case "play_next" -> musicService.next(deviceId);
            case "play_prev" -> musicService.prev(deviceId);
            case "pause" -> minaService.playerPause(deviceId);
            case "resume" -> minaService.playerPlay(deviceId);
            case "set_volume" -> {
                int vol = parseParamInt(params, "volume", 50);
                minaService.playerSetVolume(deviceId, vol);
            }
            case "set_play_mode" -> {
                int mode = parseParamInt(params, "mode", 2);
                minaService.playerSetLoop(deviceId, mode);
            }
            case "play_last" -> {
                PlayStateEntity state = playStateMapper.selectOneById(1L);
                if (state == null || state.getUrlPath() == null || state.getUrlPath().isEmpty()) {
                    throw new RuntimeException("没有上次播放记录");
                }
                String audioUrl = buildAudioUrl(state.getUrlPath());
                log.info("恢复上次播放: urlPath={}, audioUrl={}", state.getUrlPath(), audioUrl);
                musicService.play(deviceId, audioUrl);
            }
            case "tts" -> {
                String text = parseParamString(params, "text", "定时播报");
                minaService.textToSpeech(deviceId, text);
            }
            case "send_command" -> {
                String text = parseParamString(params, "text", "");
                boolean speak = parseParamBool(params, "speak", false);
                String did = parseParamString(params, "did", "");
                if (!text.isEmpty() && !did.isEmpty()) {
                    miioService.executeAction(did, 5, 4, List.of(text, speak ? 1 : 0));
                }
            }
            default -> log.warn("未知命令: {}", command);
        }
    }

    private String parseParamString(String paramsJson, String key, String defaultVal) {
        if (paramsJson == null || paramsJson.isEmpty()) return defaultVal;
        try {
            var node = objectMapper.readTree(paramsJson);
            return node.path(key).asText(defaultVal);
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private int parseParamInt(String paramsJson, String key, int defaultVal) {
        if (paramsJson == null || paramsJson.isEmpty()) return defaultVal;
        try {
            var node = objectMapper.readTree(paramsJson);
            return node.path(key).asInt(defaultVal);
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private boolean parseParamBool(String paramsJson, String key, boolean defaultVal) {
        if (paramsJson == null || paramsJson.isEmpty()) return defaultVal;
        try {
            var node = objectMapper.readTree(paramsJson);
            return node.path(key).asBoolean(defaultVal);
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private String buildAudioUrl(String urlPath) {
        try {
            FolderConfigEntity config = folderConfigMapper.selectOneById(1L);
            String base = (config != null && config.getServerUrl() != null && !config.getServerUrl().isEmpty())
                    ? config.getServerUrl() : "http://localhost:8080";
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

    private void logExecution(ScheduledTaskEntity task, boolean success, String message) {
        try {
            ScheduledTaskLogEntity logEntity = new ScheduledTaskLogEntity();
            logEntity.setTaskId(task.getId());
            logEntity.setTaskName(task.getTaskName());
            logEntity.setDeviceId(task.getDeviceId());
            logEntity.setCommand(getCommandSummary(task));
            logEntity.setSuccess(success);
            logEntity.setMessage(message);
            logEntity.setCreatedAt(OffsetDateTime.now());
            logMapper.insert(logEntity);
        } catch (Exception e) {
            log.debug("记录执行日志失败: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        taskMap.values().forEach(f -> f.cancel(false));
        taskMap.clear();
        if (scheduler instanceof ThreadPoolTaskScheduler ts) {
            ts.shutdown();
        }
    }
}
