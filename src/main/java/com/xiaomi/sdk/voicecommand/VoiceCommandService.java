package com.xiaomi.sdk.voicecommand;

import com.xiaomi.sdk.miot.MiIOService;
import com.xiaomi.sdk.model.VoiceCommandResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 语音命令服务
 * 提供发送文本命令到音箱和管理对话轮询的能力
 * @author awen
 */
public class VoiceCommandService {

    private static final Logger log = LoggerFactory.getLogger(VoiceCommandService.class);

    private final MiIOService miioService;
    private final ConversationPoller poller;

    public VoiceCommandService(MiIOService miioService, ConversationPoller poller) {
        this.miioService = miioService;
        this.poller = poller;
    }

    /**
     * 发送文本命令到音箱（MIoT action siid=5, aiid=4）
     * 等同于对音箱说"小爱同学, {text}"
     *
     * @param did   MIoT 设备 ID
     * @param text  要发送的文本命令
     * @param speak true → 音箱语音回应，false → 静默执行
     */
    public VoiceCommandResult sendTextCommand(String did, String text, boolean speak) {
        if (did == null || did.isEmpty()) {
            return VoiceCommandResult.fail("缺少设备ID(did)");
        }
        if (text == null || text.isBlank()) {
            return VoiceCommandResult.fail("缺少文本命令");
        }
        try {
            log.info("发送文本命令到音箱: did={}, text={}, speak={}", did, text, speak);
            miioService.executeAction(did, 5, 4, java.util.List.of(text, speak ? 1 : 0));
            return VoiceCommandResult.ok("send_text", text);
        } catch (Exception e) {
            log.error("发送文本命令失败: {}", e.getMessage());
            return VoiceCommandResult.fail("发送失败: " + e.getMessage());
        }
    }

    public void startPolling(String deviceId, int intervalSeconds) {
        poller.start(deviceId, intervalSeconds);
    }

    public void stopPolling() {
        poller.stop();
    }

    public boolean isPolling() {
        return poller.isRunning();
    }

    public ConversationPoller getPoller() {
        return poller;
    }
}
