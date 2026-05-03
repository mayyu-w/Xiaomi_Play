package com.xiaomi.sdk.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.xiaomi.sdk.entity.ScheduledTaskEntity;
import com.xiaomi.sdk.entity.ScheduledTaskLogEntity;
import com.xiaomi.sdk.mapper.ScheduledTaskLogMapper;
import com.xiaomi.sdk.mapper.ScheduledTaskMapper;
import com.xiaomi.sdk.schedule.CronTaskManager;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 定时任务控制器
 * @author awen
 */
@RestController
@RequestMapping("/api/schedule")
public class ScheduledTaskController {

    private final ScheduledTaskMapper taskMapper;
    private final ScheduledTaskLogMapper logMapper;
    private final CronTaskManager cronTaskManager;

    public ScheduledTaskController(ScheduledTaskMapper taskMapper,
                                    ScheduledTaskLogMapper logMapper,
                                    CronTaskManager cronTaskManager) {
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
        this.cronTaskManager = cronTaskManager;
    }

    @GetMapping("/commands")
    public ResponseEntity<Map<String, Object>> listCommands() {
        return ResponseEntity.ok(Map.of("success", true, "data", CronTaskManager.COMMAND_LIST, "message", ""));
    }

    @GetMapping("/play-modes")
    public ResponseEntity<Map<String, Object>> listPlayModes() {
        return ResponseEntity.ok(Map.of("success", true, "data", CronTaskManager.PLAY_MODE_MAP, "message", ""));
    }

    @GetMapping("/cron/validate")
    public ResponseEntity<Map<String, Object>> validateCron(@RequestParam String expr) {
        try {
            new CronTrigger(expr);
            return ResponseEntity.ok(Map.of("success", true, "data", "", "message", ""));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "data", "", "message", "Cron表达式无效"));
        }
    }

    @GetMapping("/tasks")
    public ResponseEntity<Map<String, Object>> listTasks() {
        List<ScheduledTaskEntity> tasks = taskMapper.selectListByQuery(
                QueryWrapper.create().orderBy("created_at", false));
        return ResponseEntity.ok(Map.of("success", true, "data", tasks, "message", ""));
    }

    @PostMapping("/tasks")
    public ResponseEntity<Map<String, Object>> addTask(@RequestBody Map<String, Object> body) {
        String cronExpr = (String) body.get("cronExpr");
        try {
            new CronTrigger(cronExpr);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "data", "", "message", "Cron表达式无效: " + e.getMessage()));
        }
        ScheduledTaskEntity entity = new ScheduledTaskEntity();
        entity.setTaskName((String) body.get("taskName"));
        entity.setDeviceId((String) body.get("deviceId"));
        entity.setCronExpr((String) body.get("cronExpr"));
        Object commandsObj = body.get("commands");
        if (commandsObj instanceof List<?> list && !list.isEmpty()) {
            try {
                entity.setCommand(new ObjectMapper().writeValueAsString(list));
            } catch (Exception e) {
                return ResponseEntity.ok(Map.of("success", false, "data", "", "message", "命令格式错误"));
            }
        } else {
            entity.setCommand((String) body.get("command"));
            entity.setParams(body.get("params") != null ? body.get("params").toString() : null);
        }
        entity.setEnabled(body.get("enabled") != null ? (Boolean) body.get("enabled") : true);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        taskMapper.insert(entity);

        if (Boolean.TRUE.equals(entity.getEnabled())) {
            cronTaskManager.scheduleTask(entity);
        }
        return ResponseEntity.ok(Map.of("success", true, "data", entity.getId(), "message", "任务已创建"));
    }

    @PutMapping("/tasks/{id}")
    public ResponseEntity<Map<String, Object>> updateTask(@PathVariable Long id,
                                                            @RequestBody Map<String, Object> body) {
        if (body.containsKey("cronExpr")) {
            try {
                new CronTrigger((String) body.get("cronExpr"));
            } catch (Exception e) {
                return ResponseEntity.ok(Map.of("success", false, "data", "", "message", "Cron表达式无效: " + e.getMessage()));
            }
        }
        ScheduledTaskEntity entity = taskMapper.selectOneById(id);
        if (entity == null) {
            return ResponseEntity.ok(Map.of("success", false, "data", "", "message", "任务不存在"));
        }
        if (body.containsKey("taskName")) entity.setTaskName((String) body.get("taskName"));
        if (body.containsKey("deviceId")) entity.setDeviceId((String) body.get("deviceId"));
        if (body.containsKey("cronExpr")) entity.setCronExpr((String) body.get("cronExpr"));
        if (body.containsKey("commands")) {
            try {
                entity.setCommand(new ObjectMapper().writeValueAsString(body.get("commands")));
            } catch (Exception ignored) {}
        } else if (body.containsKey("command")) {
            entity.setCommand((String) body.get("command"));
        }
        if (body.containsKey("params")) entity.setParams(body.get("params").toString());
        if (body.containsKey("enabled")) entity.setEnabled((Boolean) body.get("enabled"));
        entity.setUpdatedAt(OffsetDateTime.now());
        taskMapper.update(entity);

        // 重新调度
        cronTaskManager.cancelTask(id);
        if (Boolean.TRUE.equals(entity.getEnabled())) {
            cronTaskManager.scheduleTask(entity);
        }
        return ResponseEntity.ok(Map.of("success", true, "data", "", "message", "任务已更新"));
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Map<String, Object>> deleteTask(@PathVariable Long id) {
        cronTaskManager.cancelTask(id);
        taskMapper.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true, "data", "", "message", "任务已删除"));
    }

    @GetMapping("/logs")
    public ResponseEntity<Map<String, Object>> logs(
            @RequestParam(required = false) Long taskId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "-1") long totalRow) {
        QueryWrapper query = QueryWrapper.create();
        if (taskId != null) {
            query.where("task_id = ?", taskId);
        }
        query.orderBy("created_at", false);
        Page<ScheduledTaskLogEntity> result = logMapper.paginate(page, size, totalRow, query);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                        "list", result.getRecords(),
                        "total", result.getTotalRow(),
                        "page", result.getPageNumber(),
                        "size", result.getPageSize(),
                        "totalPage", result.getTotalPage()
                ),
                "message", ""
        ));
    }

    @DeleteMapping("/logs")
    public ResponseEntity<Map<String, Object>> clearLogs() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(7);
        logMapper.deleteByQuery(QueryWrapper.create()
                .where("created_at < '" + cutoff + "'"));
        return ResponseEntity.ok(Map.of("success", true, "data", "", "message", "日志已清理"));
    }
}
