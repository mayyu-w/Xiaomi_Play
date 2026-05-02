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

三张表：`xm_account`（账号+Token）、`xm_device`（设备）、`xm_token_history`（审计）。

`xm_account` 关键列：`user_id`、`pass_token`、`ssecurity`、`service_token`、`service_token_expire`、`io_service_token`、`io_service_token_expire`。

Token 安全：passToken/ssecurity/serviceToken/ioServiceToken 使用 AES-256-GCM 加密存储，密钥通过环境变量注入。

数据库迁移：Flyway 管理，脚本位于 `src/main/resources/db/migration/`。

## API Endpoints

### 小米侧 API

- 认证：`https://account.xiaomi.com/pass/serviceLogin`、`serviceLoginAuth2`、`securityTokenService`
- 二维码登录：`https://account.xiaomi.com/longPolling/loginUrl`（获取二维码）、`lp` URL（长轮询扫码状态）
- MIoT 控制：`https://api.io.mi.com`
- TTS/语音：`https://api.ai.mi.com`

### 本项目 Web API

- 认证：`/api/auth/login`（密码）、`/api/auth/qrcode`（二维码）、`/api/auth/qrcode/status`（轮询）、`/api/auth/logout`
- 设备：`/api/devices`
- 音乐：`/api/music/play`、`pause`、`next`、`prev`、`volume`、`status`
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

## Development Phases

1. CryptoService（加密工具）→ 2. MiAccountService（登录）→ 3. TokenManager（缓存+持久化）→ 4. MiIOService（MIoT）→ 5. MiNAService（TTS）→ 6. MusicService（音乐）→ 7. Spring Boot Starter 自动装配 → 8. 集成测试 → 9. Web 控制台（Vue 3 + Ant Design Vue）→ 10. 二维码扫码登录 → 11. Token 定时自动刷新
