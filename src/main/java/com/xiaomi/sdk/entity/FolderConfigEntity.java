package com.xiaomi.sdk.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.OffsetDateTime;

/**
 * 文件夹监控配置实体
 * @author awen
 */
@Table("xm_folder_config")
public class FolderConfigEntity {

    @Id
    private Long id;
    private String path;
    private Boolean watchEnabled;
    private Integer watchInterval;
    private String ignoreDirs;
    private Integer maxDepth;
    private String serverUrl;
    private Integer playMode;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public Boolean getWatchEnabled() { return watchEnabled; }
    public void setWatchEnabled(Boolean watchEnabled) { this.watchEnabled = watchEnabled; }

    public Integer getWatchInterval() { return watchInterval; }
    public void setWatchInterval(Integer watchInterval) { this.watchInterval = watchInterval; }

    public String getIgnoreDirs() { return ignoreDirs; }
    public void setIgnoreDirs(String ignoreDirs) { this.ignoreDirs = ignoreDirs; }

    public Integer getMaxDepth() { return maxDepth; }
    public void setMaxDepth(Integer maxDepth) { this.maxDepth = maxDepth; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }

    public Integer getPlayMode() { return playMode; }
    public void setPlayMode(Integer playMode) { this.playMode = playMode; }
}
