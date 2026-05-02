package com.xiaomi.sdk.voicecommand;

import com.mybatisflex.core.query.QueryWrapper;
import com.xiaomi.sdk.entity.VoiceCommandEntity;
import com.xiaomi.sdk.entity.VoiceCommandLogEntity;
import com.xiaomi.sdk.mapper.VoiceCommandLogMapper;
import com.xiaomi.sdk.mapper.VoiceCommandMapper;
import com.xiaomi.sdk.mina.MiNAService;
import com.xiaomi.sdk.model.VoiceCommandResult;
import com.xiaomi.sdk.music.PlayerStatusScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 语音命令匹配与路由
 * @author awen
 */
public class VoiceCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(VoiceCommandHandler.class);

    private final MiNAService minaService;
    private final PlayerStatusScheduler statusScheduler;
    private final VoiceCommandMapper commandMapper;
    private final VoiceCommandLogMapper logMapper;

    private final LinkedHashMap<String, String> builtinKeywords = new LinkedHashMap<>();
    private volatile List<VoiceCommandEntity> customKeywords = List.of();

    public VoiceCommandHandler(MiNAService minaService,
                               PlayerStatusScheduler statusScheduler,
                               VoiceCommandMapper commandMapper,
                               VoiceCommandLogMapper logMapper) {
        this.minaService = minaService;
        this.statusScheduler = statusScheduler;
        this.commandMapper = commandMapper;
        this.logMapper = logMapper;
        initDefaultKeywords();
    }

    private void initDefaultKeywords() {
        // 播放控制
        builtinKeywords.put("下一首", "play_next");
        builtinKeywords.put("上一首", "play_prev");
        builtinKeywords.put("关机", "stop");
        builtinKeywords.put("停止播放", "stop");
        builtinKeywords.put("暂停", "pause");
        builtinKeywords.put("继续播放", "resume");
        builtinKeywords.put("继续", "resume");

        // 音量
        builtinKeywords.put("大声点", "volume_up");
        builtinKeywords.put("小声点", "volume_down");
    }

    public void refreshCustomKeywords() {
        try {
            QueryWrapper qw = QueryWrapper.create()
                    .where("enabled = true")
                    .orderBy("sort_order", true);
            customKeywords = commandMapper.selectListByQuery(qw);
        } catch (Exception e) {
            log.warn("加载自定义关键词失败: {}", e.getMessage());
        }
    }

    public VoiceCommandResult handle(String query, String deviceId) {
        if (query == null || query.isBlank()) {
            return VoiceCommandResult.noMatch(query);
        }
        query = query.trim();

        // 1. 自定义关键词优先（完全匹配）
        for (VoiceCommandEntity kw : customKeywords) {
            if (query.equals(kw.getKeyword())) {
                VoiceCommandResult result = executeCustom(kw.getCommand(), query, deviceId);
                logCommand(deviceId, query, kw.getKeyword(), result);
                return result;
            }
        }

        // 2. 内置关键词完全匹配
        String action = builtinKeywords.get(query);
        if (action != null) {
            VoiceCommandResult result = executeBuiltin(action, "", query, deviceId);
            logCommand(deviceId, query, query, result);
            return result;
        }

        // 3. 模糊匹配（contains）
        for (VoiceCommandEntity kw : customKeywords) {
            if (query.contains(kw.getKeyword())) {
                VoiceCommandResult result = executeCustom(kw.getCommand(), query, deviceId);
                logCommand(deviceId, query, kw.getKeyword(), result);
                return result;
            }
        }

        for (Map.Entry<String, String> entry : builtinKeywords.entrySet()) {
            if (query.contains(entry.getKey())) {
                VoiceCommandResult result = executeBuiltin(entry.getValue(), extractArg(query, entry.getKey()), entry.getKey(), deviceId);
                logCommand(deviceId, query, entry.getKey(), result);
                return result;
            }
        }

        logCommand(deviceId, query, null, VoiceCommandResult.noMatch(query));
        return VoiceCommandResult.noMatch(query);
    }

    private String extractArg(String query, String keyword) {
        int idx = query.indexOf(keyword);
        if (idx > 0) {
            return query.substring(0, idx).trim();
        }
        int after = idx + keyword.length();
        if (after < query.length()) {
            return query.substring(after).trim();
        }
        return "";
    }

    private VoiceCommandResult executeBuiltin(String action, String arg, String keyword, String deviceId) {
        try {
            switch (action) {
                case "play_next" -> {
                    minaService.playerPlay(deviceId);
                    return VoiceCommandResult.ok(action, keyword);
                }
                case "play_prev" -> {
                    minaService.playerPlay(deviceId);
                    return VoiceCommandResult.ok(action, keyword);
                }
                case "stop" -> {
                    minaService.playerStop(deviceId);
                    return VoiceCommandResult.ok(action, keyword);
                }
                case "pause" -> {
                    minaService.playerPause(deviceId);
                    return VoiceCommandResult.ok(action, keyword);
                }
                case "resume" -> {
                    minaService.playerPlay(deviceId);
                    return VoiceCommandResult.ok(action, keyword);
                }
                case "set_play_type_one" -> {
                    minaService.playerSetLoop(deviceId, 0);
                    return VoiceCommandResult.ok(action, keyword);
                }
                case "set_play_type_all" -> {
                    minaService.playerSetLoop(deviceId, 1);
                    return VoiceCommandResult.ok(action, keyword);
                }
                case "set_play_type_rnd" -> {
                    minaService.playerSetLoop(deviceId, 2);
                    return VoiceCommandResult.ok(action, keyword);
                }
                case "set_play_type_sin" -> {
                    minaService.playerSetLoop(deviceId, 3);
                    return VoiceCommandResult.ok(action, keyword);
                }
                case "set_play_type_seq" -> {
                    minaService.playerSetLoop(deviceId, 4);
                    return VoiceCommandResult.ok(action, keyword);
                }
                case "volume_up" -> {
                    int vol = statusScheduler.getCachedStatus().volume();
                    minaService.playerSetVolume(deviceId, Math.min(vol + 10, 100));
                    return VoiceCommandResult.ok(action, keyword);
                }
                case "volume_down" -> {
                    int vol = statusScheduler.getCachedStatus().volume();
                    minaService.playerSetVolume(deviceId, Math.max(vol - 10, 0));
                    return VoiceCommandResult.ok(action, keyword);
                }
                default -> {
                    log.warn("未知的内置命令: {}", action);
                    return VoiceCommandResult.fail("未知命令: " + action);
                }
            }
        } catch (Exception e) {
            log.error("执行内置命令失败: action={}, error={}", action, e.getMessage());
            return VoiceCommandResult.fail("执行失败: " + e.getMessage());
        }
    }

    private VoiceCommandResult executeCustom(String command, String query, String deviceId) {
        log.info("执行自定义命令: command={}, query={}", command, query);
        return VoiceCommandResult.ok("custom_" + command, query);
    }

    private void logCommand(String deviceId, String query, String matchedKeyword, VoiceCommandResult result) {
        try {
            VoiceCommandLogEntity entity = new VoiceCommandLogEntity();
            entity.setDeviceId(deviceId);
            entity.setQuery(query);
            entity.setMatchedKeyword(matchedKeyword);
            entity.setCommand(result.command() != null ? result.command() : "NO_MATCH");
            entity.setSuccess(result.success());
            entity.setMessage(result.message());
            logMapper.insert(entity);
        } catch (Exception e) {
            log.debug("记录命令日志失败: {}", e.getMessage());
        }
    }

    public Map<String, String> getBuiltinKeywords() {
        return Map.copyOf(builtinKeywords);
    }
}
