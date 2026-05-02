package com.xiaomi.sdk.auto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomi.sdk.account.MiAccountService;
import com.xiaomi.sdk.account.QrCodeLoginService;
import com.xiaomi.sdk.config.XiaomiSdkProperties;
import com.xiaomi.sdk.crypto.CryptoService;
import com.xiaomi.sdk.http.OkHttpClientFactory;
import com.xiaomi.sdk.mapper.AccountMapper;
import com.xiaomi.sdk.mapper.TokenHistoryMapper;
import com.xiaomi.sdk.mina.MiNAService;
import com.xiaomi.sdk.miot.MiIOService;
import com.xiaomi.sdk.music.MusicService;
import com.xiaomi.sdk.music.PlayerStatusScheduler;
import com.xiaomi.sdk.token.TokenManager;
import com.xiaomi.sdk.token.TokenRefreshScheduler;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Xiaomi SDK Spring Boot 自动配置
 * @author awen
 */
@AutoConfiguration
@AutoConfigureAfter(name = "com.mybatisflex.spring.boot.MybatisFlexAutoConfiguration")
@ConditionalOnProperty(name = "xiaomi.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(XiaomiSdkProperties.class)
@EnableScheduling
public class XiaomiSdkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CryptoService cryptoService(XiaomiSdkProperties properties) {
        String aesKey = properties.crypto().aesKey();
        return (aesKey != null && !aesKey.isBlank())
                ? new CryptoService(aesKey)
                : new CryptoService();
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
    public TokenManager tokenManager(StringRedisTemplate redisTemplate,
                                     AccountMapper accountMapper,
                                     TokenHistoryMapper tokenHistoryMapper,
                                     CryptoService cryptoService,
                                     ObjectMapper objectMapper) {
        return new TokenManager(redisTemplate, accountMapper, tokenHistoryMapper,
                cryptoService, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public MiAccountService miAccountService(OkHttpClient httpClient,
                                              ObjectMapper objectMapper,
                                              CryptoService crypto,
                                              XiaomiSdkProperties properties,
                                              TokenManager tokenManager) {
        return new MiAccountService(httpClient, objectMapper, crypto, properties, tokenManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public QrCodeLoginService qrCodeLoginService(OkHttpClient httpClient,
                                                  ObjectMapper objectMapper,
                                                  CryptoService crypto,
                                                  XiaomiSdkProperties properties,
                                                  MiAccountService accountService) {
        return new QrCodeLoginService(httpClient, objectMapper, crypto, properties, accountService);
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
                                    XiaomiSdkProperties properties,
                                    MiIOService miioService) {
        MiNAService service = new MiNAService(httpClient, objectMapper, crypto, accountService, properties);
        service.setMiioService(miioService);
        return service;
    }

    @Bean
    @ConditionalOnMissingBean
    public MusicService musicService(MiNAService minaService) {
        return new MusicService(minaService);
    }

    @Bean
    @ConditionalOnMissingBean
    public PlayerStatusScheduler playerStatusScheduler(MiNAService minaService,
                                                        ObjectMapper objectMapper) {
        return new PlayerStatusScheduler(minaService, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenRefreshScheduler tokenRefreshScheduler(MiAccountService accountService,
                                                        TokenManager tokenManager) {
        return new TokenRefreshScheduler(accountService, tokenManager);
    }
}
