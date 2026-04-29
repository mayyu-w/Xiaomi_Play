# Xiaomi Play

小米音箱控制 Java SDK —— 将 Python 版 [MiService](https://github.com/Yonsm/MiService) + [xiaomusic](https://github.com/hanxi/xiaomusic) 合二为一，输出 Java 21 SDK，供 Spring Boot 项目直接引用。

## 功能

- 小米账号登录与 Token 管理（自动刷新、持久化）
- MIoT 协议设备控制（属性读写、动作执行）
- TTS 语音播报（通过小爱同学播报任意文本）
- 音乐播放控制（播放/暂停/切歌/歌词）

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

## 环境要求

- JDK 21
- PostgreSQL 17.x
- Redis 7.x

## License

[MIT](LICENSE)
