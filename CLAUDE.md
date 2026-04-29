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
2. Token 有效 → 直接使用；过期 → `refreshToken()` → 更新数据库
3. Token 不存在 → `MiAccountService.login(user, pwd)` → 保存到数据库
4. 获取有效 Token 后，调用方使用 MiIOService / MiNAService / MusicService

### 模块划分（com.xiaomi.sdk）

| 模块 | 职责 | 关键依赖 |
|------|------|----------|
| `MiAccountService` | 小米账号登录、Token 刷新、设备列表 | MiAccount API |
| `MiIOService` | MIoT 属性读写、动作执行 | MIoT API (api.io.mi.com) |
| `MiNAService` | TTS 文本转语音播报 | MiNA API (api.ai.mi.com) |
| `MusicService` | 播放/暂停/切歌/歌词（源自 xiaomusic） | MiNA + MIoT |
| `CryptoService` | RC4/MD5/SHA1/HMAC-SHA1 加密工具 | Java Crypto API |
| `TokenManager` | Token 缓存(Redis) + 持久化(PostgreSQL) + 自动刷新 | Spring Data Redis + MyBatis-Flex |

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

Token 安全：serviceToken/ssecurity/passToken 使用 AES-256 加密存储，密钥通过环境变量注入。

## API Endpoints（小米侧）

- 认证：`https://account.xiaomi.com/pass/serviceLogin`、`serviceLoginAuth2`、`securityTokenService`
- MIoT 控制：`https://api.io.mi.com`
- TTS/语音：`https://api.ai.mi.com`

## Configuration

配置前缀 `xiaomi.api`、`xiaomi.crypto`、`xiaomi.http`，敏感值通过环境变量注入（`${XIAOMI_AES_KEY}`、`${DB_USER}`、`${DB_PASSWORD}`）。

## Development Phases

1. CryptoService（加密工具）→ 2. MiAccountService（登录）→ 3. TokenManager（缓存+持久化）→ 4. MiIOService（MIoT）→ 5. MiNAService（TTS）→ 6. MusicService（音乐）→ 7. Spring Boot Starter 自动装配 → 8. 集成测试
