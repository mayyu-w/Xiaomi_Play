package com.xiaomi.sdk.model;

/**
 * 语音命令执行结果
 * @author awen
 */
public record VoiceCommandResult(
        boolean success,
        String command,
        String originalQuery,
        String message
) {
    public static VoiceCommandResult ok(String command, String query) {
        return new VoiceCommandResult(true, command, query, "ok");
    }

    public static VoiceCommandResult noMatch(String query) {
        return new VoiceCommandResult(false, null, query, "未匹配到命令");
    }

    public static VoiceCommandResult fail(String message) {
        return new VoiceCommandResult(false, null, null, message);
    }
}
