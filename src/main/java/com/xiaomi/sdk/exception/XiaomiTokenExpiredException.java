package com.xiaomi.sdk.exception;

/**
 * Token 过期异常
 * @author awen
 */
public class XiaomiTokenExpiredException extends RuntimeException {

    public XiaomiTokenExpiredException(String message) {
        super(message);
    }

    public XiaomiTokenExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
