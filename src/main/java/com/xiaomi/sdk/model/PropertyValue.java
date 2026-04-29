package com.xiaomi.sdk.model;

/**
 * MIoT 属性值
 * @author awen
 */
public record PropertyValue(
    String did,
    int siid,
    int piid,
    Object value,
    int code
) {}
