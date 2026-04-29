package com.xiaomi.sdk.http;

import com.xiaomi.sdk.config.XiaomiSdkProperties;
import okhttp3.OkHttpClient;

import java.time.Duration;

/**
 * OkHttpClient 工厂
 * @author awen
 */
public class OkHttpClientFactory {

    private static final String USER_AGENT_ACCOUNT =
            "APP/com.xiaomi.mihome APPV/6.0.103 iosPassportSDK/3.9.0 iOS/14.4 miHSTS";

    private static final String USER_AGENT_MIO =
            "iOS-14.4-6.0.103-iPhone12,3--D7744744F7AF32F0544445285880DD63E47D9BE9-8816080-84A3F44E137B71AE-iPhone";

    private static final String USER_AGENT_MINA =
            "MiHome/6.0.103 (com.xiaomi.mihome; build:6.0.103.1; iOS 14.4.0) Alamofire/6.0.103 MICO/iOSApp/appStore/6.0.103";

    public static String getUserAgentAccount() {
        return USER_AGENT_ACCOUNT;
    }

    public static String getUserAgentMio() {
        return USER_AGENT_MIO;
    }

    public static String getUserAgentMina() {
        return USER_AGENT_MINA;
    }

    public OkHttpClient create(XiaomiSdkProperties properties) {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(properties.http().connectTimeout()))
                .readTimeout(Duration.ofMillis(properties.http().readTimeout()))
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }
}
