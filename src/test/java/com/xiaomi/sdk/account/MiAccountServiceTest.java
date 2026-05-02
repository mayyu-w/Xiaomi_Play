package com.xiaomi.sdk.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomi.sdk.config.XiaomiSdkProperties;
import com.xiaomi.sdk.crypto.CryptoService;
import com.xiaomi.sdk.exception.XiaomiAuthException;
import com.xiaomi.sdk.http.OkHttpClientFactory;
import com.xiaomi.sdk.model.LoginResult;
import com.xiaomi.sdk.token.StubTokenManager;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MiAccountService 单元测试
 * @author awen
 */
class MiAccountServiceTest {

    private MockWebServer mockWebServer;
    private MiAccountService accountService;
    private XiaomiSdkProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("").toString();
        properties = new XiaomiSdkProperties(true,
                new XiaomiSdkProperties.Api(
                        baseUrl + "account",
                        baseUrl + "io",
                        baseUrl + "na",
                        baseUrl + "na2"
                ),
                new XiaomiSdkProperties.Crypto(),
                new XiaomiSdkProperties.Http(),
                new XiaomiSdkProperties.Folder(),
                new XiaomiSdkProperties.Voice()
        );

        OkHttpClientFactory factory = new OkHttpClientFactory();
        accountService = new MiAccountService(
                factory.create(properties),
                new ObjectMapper(),
                new CryptoService(),
                properties,
                new StubTokenManager()
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.close();
    }

    @Test
    @DisplayName("登录成功应返回 LoginResult")
    void loginShouldReturnLoginResult() throws Exception {
        // Step 1: serviceLogin(micoapi) 返回 code != 0，需要密码登录
        mockWebServer.enqueue(new MockResponse.Builder()
                .body("&&&START&&&{\"code\":1,\"qs\":\"qs_value\",\"sid\":\"micoapi\"," +
                        "\"_sign\":\"sign_value\",\"callback\":\"callback_value\"}")
                .build());

        // Step 2: serviceLoginAuth2 返回成功
        mockWebServer.enqueue(new MockResponse.Builder()
                .body("&&&START&&&{\"code\":0,\"userId\":\"12345\"," +
                        "\"passToken\":\"pt_value\",\"ssecurity\":\"c2VjdXJpdHk=\",\"nonce\":\"bm9uY2U=\"," +
                        "\"location\":\"" + mockWebServer.url("security_token_micoapi") + "\"}")
                .build());

        // Step 3: fetchServiceToken(micoapi) 返回 serviceToken cookie
        mockWebServer.enqueue(new MockResponse.Builder()
                .addHeader("Set-Cookie", "serviceToken=st_micoapi; path=/")
                .build());

        // Step 4: serviceLogin(xiaomiio) 使用 passToken 直接返回 location
        mockWebServer.enqueue(new MockResponse.Builder()
                .body("&&&START&&&{\"code\":0,\"nonce\":\"aW9ub25jZQ==\"," +
                        "\"location\":\"" + mockWebServer.url("security_token_xiaomiio") + "\"}")
                .build());

        // Step 5: fetchServiceToken(xiaomiio) 返回 xiaomiio 的 serviceToken
        mockWebServer.enqueue(new MockResponse.Builder()
                .addHeader("Set-Cookie", "serviceToken=st_xiaomiio; path=/")
                .build());

        LoginResult result = accountService.login("user@test.com", "password123");

        assertNotNull(result);
        assertEquals("12345", result.userId());
        assertEquals("pt_value", result.passToken());
        assertEquals("st_micoapi", result.serviceToken());
        assertEquals("st_xiaomiio", result.ioServiceToken());

        // 验证请求路径
        RecordedRequest req1 = mockWebServer.takeRequest();
        assertTrue(req1.getRequestLine().contains("serviceLogin"));

        RecordedRequest req2 = mockWebServer.takeRequest();
        assertTrue(req2.getRequestLine().contains("serviceLoginAuth2"));

        RecordedRequest req3 = mockWebServer.takeRequest();
        assertTrue(req3.getRequestLine().contains("security_token_micoapi"));

        RecordedRequest req4 = mockWebServer.takeRequest();
        assertTrue(req4.getRequestLine().contains("serviceLogin"));

        RecordedRequest req5 = mockWebServer.takeRequest();
        assertTrue(req5.getRequestLine().contains("security_token_xiaomiio"));
    }

    @Test
    @DisplayName("登录失败应抛出 XiaomiAuthException")
    void loginFailureShouldThrowAuthException() {
        // Step 1: serviceLogin 返回需要登录
        mockWebServer.enqueue(new MockResponse.Builder()
                .body("&&&START&&&{\"code\":1,\"qs\":\"qs\",\"sid\":\"micoapi\"," +
                        "\"_sign\":\"sign\",\"callback\":\"cb\"}")
                .build());

        // Step 2: serviceLoginAuth2 返回错误
        mockWebServer.enqueue(new MockResponse.Builder()
                .body("&&&START&&&{\"code\":87001,\"message\":\"Invalid credentials\"}")
                .build());

        assertThrows(XiaomiAuthException.class, () ->
                accountService.login("user@test.com", "wrong_password"));
    }

    @Test
    @DisplayName("未登录时获取设备列表应抛出异常")
    void getDeviceListWithoutLoginShouldThrow() {
        assertThrows(XiaomiAuthException.class, () -> accountService.getDeviceList());
    }

    @Test
    @DisplayName("登出后 currentToken 应为 null")
    void logoutShouldClearToken() {
        assertDoesNotThrow(() -> accountService.logout());
        assertNull(accountService.getCurrentToken());
    }

    @Test
    @DisplayName("buildCookieString 未登录时返回空字符串")
    void buildCookieStringWithoutLoginShouldReturnEmpty() {
        assertEquals("", accountService.buildCookieString("micoapi"));
    }
}
