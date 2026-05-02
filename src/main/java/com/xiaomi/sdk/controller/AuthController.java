package com.xiaomi.sdk.controller;

import com.xiaomi.sdk.account.MiAccountService;
import com.xiaomi.sdk.account.QrCodeLoginService;
import com.xiaomi.sdk.exception.XiaomiAuthException;
import com.xiaomi.sdk.model.LoginResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器
 * @author awen
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final MiAccountService accountService;
    private final QrCodeLoginService qrCodeLoginService;

    public AuthController(MiAccountService accountService, QrCodeLoginService qrCodeLoginService) {
        this.accountService = accountService;
        this.qrCodeLoginService = qrCodeLoginService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "data", "", "message", "用户名和密码不能为空"
            ));
        }
        try {
            LoginResult result = accountService.login(username, password);
            return ResponseEntity.ok(Map.of(
                    "success", true, "data", Map.of(
                            "userId", result.userId(),
                            "username", username,
                            "serviceTokenExpire", result.serviceTokenExpire()
                    ), "message", "ok"
            ));
        } catch (XiaomiAuthException e) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false, "data", "", "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        accountService.logout();
        return ResponseEntity.ok(Map.of("success", true, "data", "", "message", "ok"));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        LoginResult token = accountService.getCurrentToken();
        boolean loggedIn = token != null;
        Map<String, Object> data;
        if (loggedIn) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("loggedIn", true);
            m.put("userId", token.userId());
            String nickname = accountService.getCachedNickname();
            if (nickname != null && !nickname.isEmpty()) {
                m.put("nickname", nickname);
            }
            data = m;
        } else {
            data = Map.of("loggedIn", false);
        }
        return ResponseEntity.ok(Map.of("success", true, "data", data, "message", "ok"));
    }

    @GetMapping("/qrcode")
    public ResponseEntity<Map<String, Object>> qrcode() {
        try {
            Map<String, Object> result = qrCodeLoginService.initQrLogin();
            if (Boolean.TRUE.equals(result.get("alreadyLoggedIn"))) {
                return ResponseEntity.ok(Map.of(
                        "success", false, "data", "", "message", "已登录，无需扫码"
                ));
            }
            return ResponseEntity.ok(Map.of(
                    "success", true, "data", result, "message", "ok"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false, "data", "", "message", "获取二维码失败: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/qrcode/status")
    public ResponseEntity<Map<String, Object>> qrcodeStatus(@RequestParam String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "data", "", "message", "sessionId 不能为空"
            ));
        }
        Map<String, Object> result = qrCodeLoginService.checkStatus(sessionId);
        return ResponseEntity.ok(Map.of("success", true, "data", result, "message", "ok"));
    }
}
