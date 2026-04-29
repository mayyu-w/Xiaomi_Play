package com.xiaomi.sdk.auto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomi.sdk.account.MiAccountService;
import com.xiaomi.sdk.config.XiaomiSdkProperties;
import com.xiaomi.sdk.crypto.CryptoService;
import com.xiaomi.sdk.http.OkHttpClientFactory;
import com.xiaomi.sdk.mina.MiNAService;
import com.xiaomi.sdk.miot.MiIOService;
import com.xiaomi.sdk.music.MusicService;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Xiaomi SDK Spring Boot 自动配置
 * @author awen
 */
@AutoConfiguration
@ConditionalOnProperty(name = "xiaomi.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(XiaomiSdkProperties.class)
public class XiaomiSdkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CryptoService cryptoService() {
        return new CryptoService();
    }

    @Bean
    @ConditionalOnMissingBean
    public OkHttpClientFactory okHttpClientFactory() {
        return new OkHttpClientFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public OkHttpClient okHttpClient(OkHttpClientFactory factory, XiaomiSdkProperties properties) {
        return factory.create(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper xiaomiObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public MiAccountService miAccountService(OkHttpClient httpClient,
                                              ObjectMapper objectMapper,
                                              CryptoService crypto,
                                              XiaomiSdkProperties properties) {
        return new MiAccountService(httpClient, objectMapper, crypto, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public MiIOService miIOService(OkHttpClient httpClient,
                                    ObjectMapper objectMapper,
                                    CryptoService crypto,
                                    MiAccountService accountService,
                                    XiaomiSdkProperties properties) {
        return new MiIOService(httpClient, objectMapper, crypto, accountService, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public MiNAService miNAService(OkHttpClient httpClient,
                                    ObjectMapper objectMapper,
                                    CryptoService crypto,
                                    MiAccountService accountService,
                                    XiaomiSdkProperties properties) {
        return new MiNAService(httpClient, objectMapper, crypto, accountService, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public MusicService musicService(MiNAService minaService) {
        return new MusicService(minaService);
    }
}
