package com.xiaomi.sdk.controller;

import com.xiaomi.sdk.model.TtsResult;
import com.xiaomi.sdk.mina.MiNAService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * TTS 语音播报控制器
 * @author awen
 */
@RestController
@RequestMapping("/api/tts")
public class TtsController {

    private final MiNAService miNAService;

    public TtsController(MiNAService miNAService) {
        this.miNAService = miNAService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> speak(@RequestBody Map<String, String> body) {
        String deviceId = body.get("deviceId");
        String text = body.get("text");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "data", "", "message", "播报内容不能为空"
            ));
        }
        TtsResult result = miNAService.textToSpeech(deviceId, text);
        return ResponseEntity.ok(Map.of(
                "success", result.success(), "data", "", "message", result.message()
        ));
    }
}
