package com.xiaomi.sdk.model;

/**
 * TTS 播报结果
 * @author awen
 */
public record TtsResult(
    boolean success,
    String message
) {
    public static TtsResult ok() {
        return new TtsResult(true, "ok");
    }

    public static TtsResult fail(String message) {
        return new TtsResult(false, message);
    }
}
