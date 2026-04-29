package com.xiaomi.sdk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SDK 配置属性
 * @author awen
 */
@ConfigurationProperties(prefix = "xiaomi")
public record XiaomiSdkProperties(
    boolean enabled,
    Api api,
    Crypto crypto,
    Http http
) {

    public XiaomiSdkProperties() {
        this(true, new Api(), new Crypto(), new Http());
    }

    public record Api(
        String baseUrl,
        String ioUrl,
        String naUrl,
        String naUrl2
    ) {
        public Api() {
            this(
                "https://account.xiaomi.com",
                "https://api.io.mi.com/app",
                "https://api.ai.mi.com",
                "https://api2.mina.mi.com"
            );
        }
    }

    public record Crypto(
        String aesKey
    ) {
        public Crypto() {
            this(null);
        }
    }

    public record Http(
        int connectTimeout,
        int readTimeout,
        int maxRetries
    ) {
        public Http() {
            this(10000, 30000, 3);
        }
    }
}
