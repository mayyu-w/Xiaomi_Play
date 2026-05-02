package com.xiaomi.sdk.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.OffsetDateTime;

/**
 * 语音命令执行日志实体
 * @author awen
 */
@Table("xm_voice_command_log")
public class VoiceCommandLogEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;
    private String deviceId;
    private String query;
    private String matchedKeyword;
    private String command;
    private Boolean success;
    private String message;
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getMatchedKeyword() { return matchedKeyword; }
    public void setMatchedKeyword(String matchedKeyword) { this.matchedKeyword = matchedKeyword; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
