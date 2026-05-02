package com.xiaomi.sdk.model;

/**
 * 播放器状态
 * @author awen
 */
public record PlayerStatus(
    int status,
    int volume,
    String mediaType,
    String mediaId,
    long playTime,
    long duration,
    int loopType
) {}
