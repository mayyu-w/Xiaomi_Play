package com.xiaomi.sdk.controller;

import com.xiaomi.sdk.exception.XiaomiApiException;
import com.xiaomi.sdk.exception.XiaomiAuthException;
import com.xiaomi.sdk.exception.XiaomiTokenExpiredException;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/**
 * 全局异常处理 — 统一错误响应格式
 * @author awen
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(XiaomiAuthException.class)
    public ResponseEntity<Map<String, Object>> handleAuth(XiaomiAuthException e) {
        log.warn("认证失败 [{}]: {}", e.getErrorCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "success", false, "data", "", "message", "账号或密码错误"
        ));
    }

    @ExceptionHandler(XiaomiTokenExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleTokenExpired(XiaomiTokenExpiredException e) {
        log.warn("Token 过期: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "success", false, "data", "", "message", "登录已过期，请重新登录"
        ));
    }

    @ExceptionHandler(XiaomiApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(XiaomiApiException e) {
        log.error("API 调用异常 [{}]: {}", e.getErrorCode(), e.getMessage(), e);
        return ResponseEntity.status(e.getHttpStatus()).body(Map.of(
                "success", false, "data", "", "message", "操作失败，请稍后重试"
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "success", false, "data", "", "message", "请求参数错误"
        ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "success", false, "data", "", "message", "资源不存在"
        ));
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncNotUsable(AsyncRequestNotUsableException e, HttpServletResponse response) {
        // 设备切歌/停止时主动断开音频流连接，属正常行为
        log.debug("音频流连接已断开: {}", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnknown(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false, "data", "", "message", "服务异常，请稍后重试"
        ));
    }
}
