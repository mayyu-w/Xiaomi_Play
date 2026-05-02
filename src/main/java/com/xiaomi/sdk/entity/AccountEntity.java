package com.xiaomi.sdk.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.OffsetDateTime;

/**
 * 账号与 Token 实体
 * @author awen
 */
@Table("xm_account")
public class AccountEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;
    private String userId;
    private String passToken;
    private String ssecurity;
    private String serviceToken;
    private Long serviceTokenExpire;
    private String ioServiceToken;
    private Long ioServiceTokenExpire;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPassToken() { return passToken; }
    public void setPassToken(String passToken) { this.passToken = passToken; }

    public String getSsecurity() { return ssecurity; }
    public void setSsecurity(String ssecurity) { this.ssecurity = ssecurity; }

    public String getServiceToken() { return serviceToken; }
    public void setServiceToken(String serviceToken) { this.serviceToken = serviceToken; }

    public Long getServiceTokenExpire() { return serviceTokenExpire; }
    public void setServiceTokenExpire(Long serviceTokenExpire) { this.serviceTokenExpire = serviceTokenExpire; }

    public String getIoServiceToken() { return ioServiceToken; }
    public void setIoServiceToken(String ioServiceToken) { this.ioServiceToken = ioServiceToken; }

    public Long getIoServiceTokenExpire() { return ioServiceTokenExpire; }
    public void setIoServiceTokenExpire(Long ioServiceTokenExpire) { this.ioServiceTokenExpire = ioServiceTokenExpire; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
