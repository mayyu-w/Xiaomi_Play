# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

小米音箱控制 Java SDK（xiaomi-sdk-java）——将 Python 版 MiService + xiaomusic 合二为一，输出 Java 21 SDK，供 Spring Boot 项目直接引用。

源项目参考：
- MiService：https://github.com/Yonsm/MiService（小米账号认证、MIoT 协议设备控制、TTS）
- xiaomusic：https://github.com/hanxi/xiaomusic（音箱音乐播放控制）

## Build & Run

```bash
# 构建
mvn clean package

# 运行测试
mvn test

# 运行单个测试类
mvn test -Dtest=MiAccountServiceTest

# 运行单个测试方法
mvn test -Dtest=MiAccountServiceTest#testLogin

# 跳过测试打包
mvn clean package -DskipTests
```

## Architecture

### 核心流程：先认证再控制

1. `TokenManager.loadToken()` → 查 PostgreSQL/Redis
2. Token 有效 → 直接使用；即将过期 → `refreshToken()` → 更新数据库
3. Token 不存在 → `MiAccountService.login(user, pwd)` 或 `QrCodeLoginService` 扫码登录 → 保存到数据库
4. 获取有效 Token 后，调用方使用 MiIOService / MiNAService / MusicService

### Token 自动刷新策略

| 机制 | 触发时机 | 阈值 | 说明 |
|------|----------|------|------|
| 主动刷新 | 启动 30s 后首次检测，每小时定时 | 过期前 24h | `TokenRefreshScheduler` + `@Scheduled` |
| 被动刷新 | API 调用 `getCurrentToken()` | 过期前 5min | `TokenManager.isExpiringSoon()` |
| 过期告警 | 启动首次检测 | 已过期 | 日志 WARN 提示前往首页重新登录 |

### 模块划分（com.xiaomi.sdk）

| 模块 | 职责 | 关键依赖 |
|------|------|----------|
| `MiAccountService` | 小米账号登录、Token 刷新、设备列表 | MiAccount API |
| `QrCodeLoginService` | 二维码扫码登录（serviceLogin → longPolling → lp 轮询 → 回调取 Token） | MiAccount API |
| `MiIOService` | MIoT 属性读写、动作执行 | MIoT API (api.io.mi.com) |
| `MiNAService` | TTS 文本转语音播报 | MiNA API (api.ai.mi.com) |
| `MusicService` | 播放/暂停/切歌/歌词（源自 xiaomusic） | MiNA + MIoT |
| `CryptoService` | RC4/MD5/SHA1/HMAC-SHA1 加密工具 | Java Crypto API |
| `TokenManager` | Token 缓存(Redis) + 持久化(PostgreSQL) + 刷新检测 | Spring Data Redis + MyBatis-Flex |
| `TokenRefreshScheduler` | 定时刷新调度（启动检测 + 每小时续期） | Spring Scheduling |
| `PlayerStatusScheduler` | 播放状态定时轮询 + SSE 广播 + 动态轮询间隔 | ScheduledExecutorService + SseEmitter |
| `AutoPlayManager` | 后端自动切歌（5种模式），主动检测歌曲结尾 + 5秒冷却防重复触发 | MiNAService + PlayStateMapper |
| `CronTaskManager` | 动态 Cron 定时任务管理，支持多命令串行执行 | ThreadPoolTaskScheduler + CronTrigger |
| `ConversationPoller` | 对话记录轮询，拉取小爱音箱语音输入 | MiNA Conversation API + SseEmitter |
| `VoiceCommandHandler` | 语音命令匹配与路由（完全匹配→模糊匹配→参数提取） | MusicService + MiNAService + MiIOService |
| `VoiceCommandService` | 发送文本命令到音箱 + 轮询生命周期管理 | MiIOService + ConversationPoller |

### 异常体系（exception 包）

- `XiaomiAuthException` — 认证异常（密码错误/二次验证）
- `XiaomiTokenExpiredException` — Token 过期
- `XiaomiApiException` — API 调用异常（429/500）

## Tech Stack

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 LTS | Virtual Threads 异步 IO |
| Spring Boot | 3.5.14 | 框架 |
| Maven | 3.9.x | 构建 |
| OkHttp | 5.3.2 | HTTP/2 连接复用 |
| sa-token | 1.45.0 | 内部 Token 安全管理 |
| jjwt | 0.13.0 | JWT Token 序列化 |
| MyBatis-Flex | 1.11.6 | ORM |
| PostgreSQL | 17.x | Token 持久化 |
| Redis | 7.4.8 | Token 缓存 |
| JUnit 5 + Mockito | Latest | 测试 |

## Database

数据库迁移：Flyway 管理，脚本位于 `src/main/resources/db/migration/`（V1~V11）。

| 表 | 迁移 | 用途 |
|----|------|------|
| `xm_account` | V1 | 账号+Token。关键列：`user_id`、`pass_token`、`ssecurity`、`service_token`、`service_token_expire`、`io_service_token`、`io_service_token_expire`。Token 安全：passToken/ssecurity/serviceToken/ioServiceToken 使用 AES-256-GCM 加密存储 |
| `xm_device` | V1 | 设备列表 |
| `xm_token_history` | V1 | Token 审计日志 |
| `xm_folder_config` | V3, V4, V6 | 文件夹配置（SMB 路径、服务器 URL、播放模式） |
| `xm_play_history` | V5 | 播放历史 |
| `xm_play_state` | V7 | 当前播放状态（folder_path、file_name、url_path、file_path），单记录（id=1） |
| `xm_voice_command` | V8 | 语音命令关键词配置 |
| `xm_voice_command_log` | V9 | 语音命令执行日志 |
| `xm_scheduled_task` | V10, V11 | 定时任务（cron_expr、command（JSON 多命令数组）、params、enabled） |
| `xm_scheduled_task_log` | V10 | 定时任务执行日志 |

## API Endpoints

### 小米侧 API

- 认证：`https://account.xiaomi.com/pass/serviceLogin`、`serviceLoginAuth2`、`securityTokenService`
- 二维码登录：`https://account.xiaomi.com/longPolling/loginUrl`（获取二维码）、`lp` URL（长轮询扫码状态）
- MIoT 控制：`https://api.io.mi.com`
- TTS/语音：`https://api.ai.mi.com`

### 本项目 Web API

- 认证：`/api/auth/login`（密码）、`/api/auth/qrcode`（二维码）、`/api/auth/qrcode/status`（轮询）、`/api/auth/logout`
- 设备：`/api/devices`
- 音乐：`/api/music/play`、`pause`、`next`、`prev`、`volume`、`resume`、`mode`、`status`、`status/stream`（SSE）
- 音乐扩展：`/api/music/autoplay/disable`、`/api/music/status/interval`
- 文件夹：`/api/folder/list`、`/api/folder/audio/*`、`/api/folder/config`
- 定时任务：`/api/schedule/commands`、`/api/schedule/play-modes`、`/api/schedule/cron/validate`、`/api/schedule/tasks`（CRUD）、`/api/schedule/logs`
- 语音命令：`/api/voice/send`、`/api/voice/polling/start`、`/api/voice/polling/stop`、`/api/voice/polling/status`、`/api/voice/stream`（SSE）、`/api/voice/keywords`（CRUD）、`/api/voice/config`
- TTS：`/api/tts`

### 前端

Vue 3 + Ant Design Vue（CDN 引入），SPA 单体架构。静态资源在 `src/main/resources/static/`。
- `index.html` — 入口
- `css/app.css` — 全局样式 + 主题覆盖（主色 #FF6900）
- `js/app.js` — Vue 应用、路由、所有页面组件
- `SpaController` 将非 API 请求转发到 `index.html`

## Configuration

配置前缀 `xiaomi.api`、`xiaomi.crypto`、`xiaomi.http`，敏感值通过环境变量注入（`${XIAOMI_AES_KEY}`、`${DB_USER}`、`${DB_PASSWORD}`）。

## 疑难杂症记录

### TTS 部分音箱无声音（MIoT 签名失败）

**现象**：L05B 等型号音箱调用 MiNA `text_to_speech`（mibrain）返回成功但无声音；改用 MIoT action 后报 `invalid signature`。

**根因有三，缺一不可**：

1. **mibrain TTS 不通用**：18 种型号（L05B/L05C/LX06/LX01/OH2/OH2P/S12/L15A/LX5A/LX05/X10A/L17A/ASX4B/L06A/X6A/X08E/L09A/LX04）不支持 MiNA mibrain TTS，必须走 MIoT action 协议。`MiNAService.TTS_COMMAND` 维护了型号→[siid, aiid]的映射表，`textToSpeech()` 自动识别并分发。

2. **ssecurity 按 SID 不同**：每次 `serviceLogin(sid)` 返回的 ssecurity 因 SID 而异。micoapi 的 ssecurity（如 `vAp4ayyt...`）和 xiaomiio 的 ssecurity（如 `KADEpr6X...`）完全不同。MIoT 请求签名**必须**用 xiaomiio SID 的 ssecurity。`MiAccountService.ioSsecurity` 字段（瞬态，不持久化）专门存储它，通过 `getSsecurityForMiot()` 惰性获取。

3. **ssecurity 与 ioServiceToken 必须配对**：每次 `serviceLogin("xiaomiio")` 生成**新的** ssecurity + ioServiceToken 对。Cookie 中的 serviceToken 和签名用的 ssecurity 必须来自**同一次** serviceLogin，否则签名校验失败。`getSsecurityForMiot()` 在获取新 ssecurity 的同时更新 `currentToken.ioServiceToken`，保持配对一致。

4. **请求体必须 FormBody（URL-encoded）**：MIoT 接口要求 `application/x-www-form-urlencoded` 格式。nonce/signature 含 Base64 字符（`+`/`/`/`=`），raw body 不编码时服务端会把 `+` 解释为空格导致验签失败。必须用 `FormBody` 让 OkHttp 自动 URL-encode，服务端解码后还原原始值再验签。

**关键代码路径**：`MiNAService.textToSpeech()` → 设备型号查 `TTS_COMMAND` → `MiIOService.executeAction()` → `miioRequest()` → `CryptoService.signData(uri, json, xiaomiioSsecurity)` + `FormBody` + Cookie 含配对的 `ioServiceToken`。

### URL 编码：Java URLEncoder 的 + vs %20

`java.net.URLEncoder.encode()` 把空格编码为 `+`，但 MiNA `play_by_music_url` 的 URL 路径中 `+` 不会被还原为空格，必须用 `%20`。所有 `buildAudioUrl()` 方法在编码后都要 `.replace("+", "%20")`。影响：`CronTaskManager`、`AutoPlayManager`。

### MyBatis-Flex createdAt/updatedAt 不自动填充

MyBatis-Flex 的 `@Column(onInsertValue=...)` 注解不会自动填充 `OffsetDateTime` 类型的列（需要自行设置）。所有 Entity 的 insert/update 操作必须显式 `setCreatedAt(OffsetDateTime.now())` / `setUpdatedAt(OffsetDateTime.now())`。

### playByMusicUrl REPLACE_ALL 重置设备播放模式

MiNA `play_by_music_url` 的 `REPLACE_ALL` 模式会重置设备播放模式为默认值。在 `CronTaskManager` 和 `AutoPlayManager` 中，调用 play 后必须重新通过 `minaService.playerSetLoop()` 设置播放模式。

## Development Phases

1. CryptoService（加密工具）→ 2. MiAccountService（登录）→ 3. TokenManager（缓存+持久化）→ 4. MiIOService（MIoT）→ 5. MiNAService（TTS）→ 6. MusicService（音乐）→ 7. Spring Boot Starter 自动装配 → 8. 集成测试 → 9. Web 控制台（Vue 3 + Ant Design Vue）→ 10. 二维码扫码登录 → 11. Token 定时自动刷新 → 12. 定时任务（多命令串行 + Cron 调度）→ 13. 后端自动切歌（前后端共存模型）→ 14. 语音命令（对话轮询 + 命令匹配）

## 核心机制

### 定时任务多命令串行执行

`CronTaskManager.executeTask()` 解析 `command` 字段为 JSON 数组，每个元素是 `{"cmd":"...", "params":{...}}`，命令间间隔 1.5s 顺序执行。向后兼容单字符串格式。

支持的命令：play_next、play_prev、stop、pause、resume、set_volume、set_play_mode、play_last、tts、send_command。

`play_last` 恢复上次播放后，`playByMusicUrl` 的 REPLACE_ALL 会重置设备播放模式，因此自动重新设置播放模式（任务指定的优先，否则取 `xm_folder_config.play_mode`），并启用 `AutoPlayManager`。

### 前后端共存切歌模型

- **后端控制**（默认）：`AutoPlayManager` 通过 `PlayerStatusScheduler` 轮询检测歌曲结尾，自动播放下一首。主动检测 `playTime >= duration` 时提前切歌，避免设备自动循环后再切。5 秒冷却防重复触发。
- **前端接管**：用户点击任何播放控制按钮时调用 `takeControl()` → 禁用 AutoPlayManager → 前端 `songEndTimer` 接管切歌。
- **SSE 推送**：`PlayerStatusScheduler.ssePayload()` 包含 `autoPlay`（boolean）和 `currentUrlPath`（string），前端据此判断控制权归属并显示播放来源标签。

### AutoPlayManager 播放模式

| 模式值 | 名称 | 行为 |
|--------|------|------|
| 0 | 单曲循环 | 重复当前歌曲 |
| 1 | 全部循环 | 下一首（末尾回到第一首） |
| 2 | 当前随机 | 文件夹内随机（不重复上一首） |
| 3 | 单曲播放 | 播完停止 |
| 4 | 顺序播放 | 按序播放，末首播完停止 |

仅扫描当前文件夹（`xm_play_state.folder_path`），按文件名排序与前端一致。
