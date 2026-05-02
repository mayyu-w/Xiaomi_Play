package com.xiaomi.sdk.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.OffsetDateTime;

/**
 * 设备实体
 * @author awen
 */
@Table("xm_device")
public class DeviceEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;
    private String did;
    private String name;
    private String model;
    private String token;
    private String deviceId;
    private String hardware;
    private String accountUserId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDid() { return did; }
    public void setDid(String did) { this.did = did; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getHardware() { return hardware; }
    public void setHardware(String hardware) { this.hardware = hardware; }

    public String getAccountUserId() { return accountUserId; }
    public void setAccountUserId(String accountUserId) { this.accountUserId = accountUserId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
