package com.xiaomi.sdk.model;

/**
 * 小米设备信息
 * @author awen
 */
public record Device(
    String did,
    String name,
    String model,
    String token,
    String deviceId,
    String hardware
) {}
