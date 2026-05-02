package com.xiaomi.sdk.model;

/**
 * 登录结果
 * @author awen
 */
public record LoginResult(
    String userId,
    String passToken,
    String ssecurity,
    String serviceToken,
    long serviceTokenExpire,
    String ioServiceToken,
    long ioServiceTokenExpire
) {}
