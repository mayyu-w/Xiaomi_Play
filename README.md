# Xiaomi Play

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-3.9-C71A36?logo=apachemaven&logoColor=white)
![OkHttp](https://img.shields.io/badge/OkHttp-5.3-009688?logo=google&logoColor=white)
![Vue 3](https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotjs&logoColor=white)
![Ant Design Vue](https://img.shields.io/badge/Ant%20Design%20Vue-4-0170FE?logo=antdesign&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue?logo=github)


### 小米音箱控制 Java Springboot版

## 功能

- 小米账号登录与 Token 管理（自动刷新、持久化）
- **密码登录** + **二维码扫码登录**（米家 APP 扫码）
- MIoT 协议设备控制（属性读写、动作执行）
- TTS 语音播报（通过小爱同学播报任意文本）
- 音乐播放控制（播放/暂停/切歌/歌词）
- Web 控制台（Vue 3 + Ant Design Vue，SPA 单体架构）

## 技术栈

| 技术 | 版本 |
|------|------|
| Java | 21 LTS |
| Spring Boot | 3.5.14 |
| Maven | 3.9.x |
| OkHttp | 5.3.2 |
| MyBatis-Flex | 1.11.6 |
| PostgreSQL | 17.x |
| Redis | 7.4.8 |

## 构建

```bash
mvn clean package
```

## 运行测试

```bash
mvn test
```

## Docker 部署

使用 Docker Compose 一键部署（Spring Boot + PostgreSQL + Redis）。

### 克隆项目

```bash
git clone https://github.com/mayyu-w/Xiaomi_Play.git
cd Xiaomi_Play
```

### 初始化目录与权限

```bash
# 创建数据目录
mkdir -p data/postgres data/redis logs music

# PostgreSQL 容器内以 uid 70 (postgres 用户) 运行，需要目录所有权匹配
sudo chown -R 70:70 data/postgres

# Redis 容器内以 uid 999 (redis 用户) 运行
sudo chown -R 999:999 data/redis

# 应用日志目录，当前用户可写即可
chmod 755 logs
```

> **说明**：如果 `data/postgres` 或 `data/redis` 权限不正确，容器启动时会报 `Permission denied` 或 `data directory has wrong ownership` 错误。

### 环境变量

| 变量 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `SERVER_PORT` | 否 | `8080` | Web 服务端口 |
| `XIAOMI_AES_KEY` | 否 | 空 | AES-256 密钥，用于加密数据库中的敏感 Token 字段 |
| `DB_NAME` | 否 | `xiaomi_play` | PostgreSQL 数据库名 |
| `DB_USER` | 否 | `postgres` | PostgreSQL 用户名 |
| `DB_PASSWORD` | 否 | `postgres` | PostgreSQL 密码（**生产环境务必修改**） |
| `MUSIC_DIR` | 否 | `./music` | 宿主机音乐文件目录 |

环境变量通过 `docker compose up -d` 启动时传入，传递链路：**宿主机环境变量 → docker-compose.yml → 容器内环境变量 → application.yml**。

#### Linux / macOS

```bash
# 方式一：同一行指定（推荐）
SERVER_PORT=9090 \
XIAOMI_AES_KEY=your_32_byte_key \
DB_PASSWORD=your_secure_password \
MUSIC_DIR=/srv/music \
docker compose up -d

# 方式二：export 后启动
export SERVER_PORT=9090
export XIAOMI_AES_KEY=your_32_byte_key
export DB_PASSWORD=your_secure_password
export MUSIC_DIR=/srv/music
docker compose up -d

# 方式三：写入 .env 文件（推荐用于长期部署）
cat > .env << 'EOF'
SERVER_PORT=9090
XIAOMI_AES_KEY=your_32_byte_key
DB_PASSWORD=your_secure_password
MUSIC_DIR=/srv/music
EOF
docker compose up -d
```

#### Windows (PowerShell)

```powershell
# 方式一：同一行指定
$env:SERVER_PORT="9090"; `
$env:XIAOMI_AES_KEY="your_32_byte_key"; `
$env:DB_PASSWORD="your_secure_password"; `
$env:MUSIC_DIR="D:\music"; `
docker compose up -d

# 方式二：逐行设置后启动
$env:SERVER_PORT = "9090"
$env:XIAOMI_AES_KEY = "your_32_byte_key"
$env:DB_PASSWORD = "your_secure_password"
$env:MUSIC_DIR = "D:\music"
docker compose up -d

# 方式三：写入 .env 文件（推荐用于长期部署）
@"
SERVER_PORT=9090
XIAOMI_AES_KEY=your_32_byte_key
DB_PASSWORD=your_secure_password
MUSIC_DIR=D:\music
"@ | Out-File -Encoding utf8 .env
docker compose up -d
```

> **提示**：`.env` 文件放在项目根目录（与 `docker-compose.yml` 同级），`docker compose` 会自动加载。建议将 `.env` 加入 `.gitignore` 避免泄露密钥。

### XIAOMI_AES_KEY 说明

- **长度**：恰好 **32 字节**（即 32 个 ASCII 字符，不能含中文）
- **算法**：AES-256-GCM（认证加密）
- **用途**：加密 `xm_account` 表中的 `pass_token`、`ssecurity`、`service_token`、`io_service_token`
- ⚠️ <span style="color:red">**未配置**：敏感字段明文存储（仅限开发环境）</span>
- **生成示例**：

```bash
# Linux/macOS
head -c 32 /dev/urandom | base64 | head -c 32

# Windows PowerShell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]]).Substring(0,32)
```

### 目录挂载说明

`docker-compose.yml` 中所有 `volumes` 和 `ports` 的格式均为 **`宿主机:容器内`**：

| 配置项 | 宿主机 | 容器内 | 用途 |
|--------|--------|--------|------|
| postgres volumes | `./data/postgres` | `/var/lib/postgresql/data` | PostgreSQL 数据文件 |
| redis volumes | `./data/redis` | `/data` | Redis 持久化文件（AOF） |
| app volumes | `./logs` | `/app/logs` | 应用日志（按天滚动，保留 30 天） |
| app volumes | `${MUSIC_DIR:-./music}` | `/music` | 音乐文件（只读） |
| app ports | `${SERVER_PORT:-8080}` | `${SERVER_PORT:-8080}` | Web 服务端口 |

### 启动与停止

```bash
# 首次 / 日常启动
docker compose up -d

# 代码有更新，重新构建后启动
docker compose up -d --build

# 停止
docker compose down

# 停止并删除数据（慎用）
docker compose down -v
```

### 常用运维命令

```bash
# 查看日志
docker compose logs -f app

# 查看所有容器状态
docker compose ps

# 重启单个服务
docker compose restart app
```

## 本地开发

### 环境要求

- JDK 21
- PostgreSQL 17.x
- Redis 7.x

### 配置

启动前需设置环境变量（参考上方环境变量表），或直接修改 `src/main/resources/application.yml`。

## Web 控制台

启动应用后访问 `http://localhost:8080`，进入 Web 控制台。

### 登录方式

| 方式 | 说明 |
|------|------|
| 密码登录 | 输入小米账号邮箱/手机号/小米ID + 密码 |
| 扫码登录 | 使用小米手机或米家 APP 扫描二维码，自动完成登录 |

### Token 自动刷新

| 机制 | 触发时机 | 说明 |
|------|----------|------|
| 主动刷新 | 启动后 30 秒检测，每小时定时检查 | Token 过期前 24 小时自动续期 |
| 被动刷新 | API 调用时 | Token 过期前 5 分钟自动续期 |
| 过期告警 | 启动检测 | Token 已过期时日志提示重新登录 |

## API Endpoints

### 认证 API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/auth/login` | POST | 密码登录 `{ username, password }` |
| `/api/auth/qrcode` | GET | 获取二维码图片和会话 ID |
| `/api/auth/qrcode/status` | GET | 轮询扫码状态 `?sessionId=xxx` |
| `/api/auth/status` | GET | 查询登录状态 |
| `/api/auth/logout` | POST | 退出登录 |

### 设备 API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/devices` | GET | 获取设备列表 |

### 音乐 API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/music/play` | POST | 播放音乐 `{ deviceId, url }` |
| `/api/music/pause` | POST | 暂停 `{ deviceId }` |
| `/api/music/next` | POST | 下一首 `{ deviceId }` |
| `/api/music/prev` | POST | 上一首 `{ deviceId }` |
| `/api/music/volume` | POST | 设置音量 `{ deviceId, volume }` |
| `/api/music/status` | GET | 播放状态 `?deviceId=xxx` |

### TTS API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/tts` | POST | 语音播报 `{ deviceId, text }` |

## 感谢以下项目
 [MiService](https://github.com/yihong0618/MiService) 

 [MiService](https://github.com/Yonsm/MiService)

 [xiaomusic](https://github.com/hanxi/xiaomusic)

 [米家产品库](https://home.miot-spec.com/)

## License

[MIT](LICENSE)
