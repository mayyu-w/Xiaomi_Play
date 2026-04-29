package com.xiaomi.sdk.exception;

/**
 * 小米 API 调用异常
 * @author awen
 */
public class XiaomiApiException extends RuntimeException {

    private final int httpStatus;
    private final String errorCode;

    public XiaomiApiException(int httpStatus, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public XiaomiApiException(int httpStatus, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
