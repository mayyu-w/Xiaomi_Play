package com.xiaomi.sdk.model;

import java.util.List;

/**
 * 歌词结果
 * @author awen
 */
public record LyricResult(
    boolean success,
    List<LyricLine> lines
) {
    public record LyricLine(long timestamp, String text) {}
}
