package com.xiaomi.sdk.exception;

/**
 * 小米认证异常
 * @author awen
 */
public class XiaomiAuthException extends RuntimeException {

    private final String errorCode;

    public XiaomiAuthException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public XiaomiAuthException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
