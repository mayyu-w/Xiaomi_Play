package com.xiaomi.sdk;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 小米音箱控制台
 * 基于 MiService + xiaomusic 重构的 Java SDK
 * @author awen
 */
@SpringBootApplication
@MapperScan("com.xiaomi.sdk.mapper")
public class XiaomiApplication {

    public static void main(String[] args) {
        SpringApplication.run(XiaomiApplication.class, args);
    }
}
