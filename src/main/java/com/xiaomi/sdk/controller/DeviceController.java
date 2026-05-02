package com.xiaomi.sdk.controller;

import com.xiaomi.sdk.account.MiAccountService;
import com.xiaomi.sdk.exception.XiaomiAuthException;
import com.xiaomi.sdk.model.Device;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 设备控制器
 * @author awen
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final MiAccountService accountService;

    public DeviceController(MiAccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        try {
            List<Device> devices = accountService.getDeviceList();
            return ResponseEntity.ok(Map.of("success", true, "data", devices, "message", "ok"));
        } catch (XiaomiAuthException e) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false, "data", "", "message", "未登录或登录已过期"
            ));
        }
    }
}
