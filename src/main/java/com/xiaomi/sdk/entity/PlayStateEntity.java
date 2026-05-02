package com.xiaomi.sdk.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.OffsetDateTime;

/**
 * 播放状态（单记录）
 * @author awen
 */
@Table("xm_play_state")
public class PlayStateEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;
    private String folderPath;
    private String fileName;
    private String filePath;
    private String urlPath;
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFolderPath() { return folderPath; }
    public void setFolderPath(String folderPath) { this.folderPath = folderPath; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getUrlPath() { return urlPath; }
    public void setUrlPath(String urlPath) { this.urlPath = urlPath; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
