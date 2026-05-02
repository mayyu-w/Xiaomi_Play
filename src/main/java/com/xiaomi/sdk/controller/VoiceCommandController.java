package com.xiaomi.sdk.controller;

import com.mybatisflex.core.query.QueryWrapper;
import com.xiaomi.sdk.entity.VoiceCommandEntity;
import com.xiaomi.sdk.entity.VoiceConfigEntity;
import com.xiaomi.sdk.mapper.VoiceCommandMapper;
import com.xiaomi.sdk.mapper.VoiceConfigMapper;
import com.xiaomi.sdk.model.VoiceCommandResult;
import com.xiaomi.sdk.voicecommand.ConversationPoller;
import com.xiaomi.sdk.voicecommand.VoiceCommandHandler;
import com.xiaomi.sdk.voicecommand.VoiceCommandService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 语音命令控制器
 * @author awen
 */
@RestController
@RequestMapping("/api/voice")
public class VoiceCommandController {

    private final VoiceCommandService voiceCommandService;
    private final ConversationPoller poller;
    private final VoiceCommandMapper commandMapper;
    private final VoiceConfigMapper configMapper;
    private final VoiceCommandHandler handler;

    public VoiceCommandController(VoiceCommandService voiceCommandService,
                                   ConversationPoller poller,
                                   VoiceCommandMapper commandMapper,
                                   VoiceConfigMapper configMapper,
                                   VoiceCommandHandler handler) {
        this.voiceCommandService = voiceCommandService;
        this.poller = poller;
        this.commandMapper = commandMapper;
        this.configMapper = configMapper;
        this.handler = handler;
    }

    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendTextCommand(@RequestBody Map<String, Object> body) {
        String did = (String) body.get("did");
        String text = (String) body.get("text");
        boolean speak = Boolean.TRUE.equals(body.get("speak"));

        VoiceCommandResult result = voiceCommandService.sendTextCommand(did, text, speak);
        return ResponseEntity.ok(Map.of(
                "success", result.success(), "data", "", "message", result.message()
        ));
    }

    @PostMapping("/polling/start")
    public ResponseEntity<Map<String, Object>> startPolling(@RequestBody Map<String, Object> body) {
        String deviceId = (String) body.get("deviceId");
        int interval = body.get("interval") != null ? ((Number) body.get("interval")).intValue() : 1;
        voiceCommandService.startPolling(deviceId, interval);
        saveConfig("polling_enabled", "true");
        saveConfig("polling_device_id", deviceId);
        saveConfig("polling_interval", String.valueOf(interval));
        return ResponseEntity.ok(Map.of("success", true, "data", "", "message", "轮询已启动"));
    }

    @PostMapping("/polling/stop")
    public ResponseEntity<Map<String, Object>> stopPolling() {
        voiceCommandService.stopPolling();
        saveConfig("polling_enabled", "false");
        return ResponseEntity.ok(Map.of("success", true, "data", "", "message", "轮询已停止"));
    }

    @GetMapping("/polling/status")
    public ResponseEntity<Map<String, Object>> pollingStatus() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", poller.isRunning());
        data.put("deviceId", poller.getActiveDeviceId());
        data.put("interval", poller.getIntervalSeconds());
        return ResponseEntity.ok(Map.of("success", true, "data", data, "message", ""));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        try {
            return poller.createEmitter();
        } catch (Exception e) {
            SseEmitter emitter = new SseEmitter(0L);
            emitter.completeWithError(e);
            return emitter;
        }
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        List<VoiceConfigEntity> configs = configMapper.selectAll();
        Map<String, String> data = new LinkedHashMap<>();
        for (VoiceConfigEntity c : configs) {
            data.put(c.getConfigKey(), c.getConfigValue());
        }
        return ResponseEntity.ok(Map.of("success", true, "data", data, "message", ""));
    }

    @GetMapping("/keywords")
    public ResponseEntity<Map<String, Object>> listKeywords() {
        List<VoiceCommandEntity> custom = commandMapper.selectAll();
        Map<String, String> builtin = handler.getBuiltinKeywords();
        Map<String, Object> data = Map.of("builtin", builtin, "custom", custom);
        return ResponseEntity.ok(Map.of("success", true, "data", data, "message", ""));
    }

    @PostMapping("/keywords")
    public ResponseEntity<Map<String, Object>> addKeyword(@RequestBody Map<String, Object> body) {
        VoiceCommandEntity entity = new VoiceCommandEntity();
        entity.setKeyword((String) body.get("keyword"));
        entity.setCommand((String) body.get("command"));
        entity.setEnabled(body.get("enabled") != null ? (Boolean) body.get("enabled") : true);
        entity.setSortOrder(body.get("sortOrder") != null ? ((Number) body.get("sortOrder")).intValue() : 0);
        commandMapper.insert(entity);
        handler.refreshCustomKeywords();
        return ResponseEntity.ok(Map.of("success", true, "data", "", "message", "关键词已添加"));
    }

    @PutMapping("/keywords/{id}")
    public ResponseEntity<Map<String, Object>> updateKeyword(@PathVariable Long id,
                                                              @RequestBody Map<String, Object> body) {
        VoiceCommandEntity entity = commandMapper.selectOneById(id);
        if (entity == null) {
            return ResponseEntity.ok(Map.of("success", false, "data", "", "message", "关键词不存在"));
        }
        if (body.containsKey("keyword")) entity.setKeyword((String) body.get("keyword"));
        if (body.containsKey("command")) entity.setCommand((String) body.get("command"));
        if (body.containsKey("enabled")) entity.setEnabled((Boolean) body.get("enabled"));
        entity.setUpdatedAt(OffsetDateTime.now());
        commandMapper.update(entity);
        handler.refreshCustomKeywords();
        return ResponseEntity.ok(Map.of("success", true, "data", "", "message", "关键词已更新"));
    }

    @DeleteMapping("/keywords/{id}")
    public ResponseEntity<Map<String, Object>> deleteKeyword(@PathVariable Long id) {
        commandMapper.deleteById(id);
        handler.refreshCustomKeywords();
        return ResponseEntity.ok(Map.of("success", true, "data", "", "message", "关键词已删除"));
    }

    private void saveConfig(String key, String value) {
        try {
            VoiceConfigEntity existing = configMapper.selectOneByQuery(
                    QueryWrapper.create().where("config_key = '" + key + "'"));
            if (existing != null) {
                existing.setConfigValue(value);
                existing.setUpdatedAt(OffsetDateTime.now());
                configMapper.update(existing);
            } else {
                VoiceConfigEntity entity = new VoiceConfigEntity();
                entity.setConfigKey(key);
                entity.setConfigValue(value);
                configMapper.insert(entity);
            }
        } catch (Exception e) {
            // ignore
        }
    }
}
