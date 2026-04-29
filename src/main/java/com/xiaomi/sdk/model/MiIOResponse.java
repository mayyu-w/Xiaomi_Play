package com.xiaomi.sdk.model;

/**
 * MIoT 操作响应
 * @author awen
 */
public record MiIOResponse(
    int code,
    Object result
) {}
