package com.xiaomi.sdk.model;

/**
 * 音乐操作结果
 * @author awen
 */
public record MusicResult(
    boolean success,
    String message
) {
    public static MusicResult ok() {
        return new MusicResult(true, "ok");
    }

    public static MusicResult fail(String message) {
        return new MusicResult(false, message);
    }
}
