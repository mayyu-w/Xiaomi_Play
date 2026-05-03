package com.xiaomi.sdk.exception;

/**
 * 小米认证异常（httpStatus 固定 401）
 * 继承 XiaomiApiException 以确保 catch(XiaomiApiException) 也能捕获认证异常
 * @author awen
 */
public class XiaomiAuthException extends XiaomiApiException {

    public XiaomiAuthException(String errorCode, String message) {
        super(401, errorCode, message);
    }

    public XiaomiAuthException(String errorCode, String message, Throwable cause) {
        super(401, errorCode, message, cause);
    }
}
