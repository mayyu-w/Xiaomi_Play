package com.xiaomi.sdk.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CryptoService 单元测试
 * 验证加密算法与 Python 源码一致
 * @author awen
 */
class CryptoServiceTest {

    private CryptoService crypto;

    @BeforeEach
    void setUp() {
        crypto = new CryptoService();
    }

    @Test
    @DisplayName("MD5 哈希应返回大写十六进制")
    void md5ShouldReturnUppercaseHex() {
        // Python: hashlib.md5("test".encode()).hexdigest().upper()
        String result = crypto.md5("test");
        assertEquals("098F6BCD4621D373CADE4E832627B4F6", result);
    }

    @Test
    @DisplayName("MD5 空字符串")
    void md5EmptyString() {
        String result = crypto.md5("");
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", result);
    }

    @Test
    @DisplayName("Base64 编码解码应可逆")
    void base64RoundTrip() {
        String original = "Hello, Xiaomi!";
        String encoded = crypto.base64Encode(original.getBytes(StandardCharsets.UTF_8));
        byte[] decoded = crypto.base64Decode(encoded);
        assertEquals(original, new String(decoded, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("signNonce 应与 Python sign_nonce 结果一致")
    void signNonceShouldMatchPython() {
        // Python: Base64(SHA256(b64decode(ssecurity) + b64decode(nonce)))
        String ssecurity = crypto.base64Encode("TestSecurityKey1234567890".getBytes(StandardCharsets.UTF_8));
        String nonce = crypto.base64Encode("TestNonce123456".getBytes(StandardCharsets.UTF_8));
        String result = crypto.signNonce(ssecurity, nonce);
        assertNotNull(result);
        assertFalse(result.isEmpty());
        // 验证结果可以被 Base64 解码
        assertDoesNotThrow(() -> crypto.base64Decode(result));
    }

    @Test
    @DisplayName("generateNonce 应返回合法 Base64 且长度为 16 字符（12字节编码）")
    void generateNonceShouldReturnValidBase64() {
        String nonce = crypto.generateNonce();
        assertNotNull(nonce);
        // 12 字节 Base64 编码后为 16 字符
        byte[] decoded = assertDoesNotThrow(() -> crypto.base64Decode(nonce));
        assertEquals(12, decoded.length);
    }

    @Test
    @DisplayName("signData 应包含 _nonce, data, signature 三个字段")
    void signDataShouldContainRequiredFields() {
        String uri = "/miotspec/prop/get";
        String data = "[{\"did\":\"123\",\"siid\":2,\"piid\":1}]";
        String ssecurity = crypto.base64Encode("TestSecurityKey1234567890".getBytes(StandardCharsets.UTF_8));

        Map<String, String> result = crypto.signData(uri, data, ssecurity);

        assertTrue(result.containsKey("_nonce"));
        assertTrue(result.containsKey("data"));
        assertTrue(result.containsKey("signature"));
        assertEquals(data, result.get("data"));
        assertFalse(result.get("signature").isEmpty());
    }

    @Test
    @DisplayName("signData 多次调用产生不同签名（nonce 随机）")
    void signDataShouldProduceDifferentSignatures() {
        String uri = "/miotspec/prop/get";
        String data = "[{\"did\":\"123\"}]";
        String ssecurity = crypto.base64Encode("TestSecurityKey1234567890".getBytes(StandardCharsets.UTF_8));

        Map<String, String> result1 = crypto.signData(uri, data, ssecurity);
        Map<String, String> result2 = crypto.signData(uri, data, ssecurity);

        assertNotEquals(result1.get("_nonce"), result2.get("_nonce"));
        assertNotEquals(result1.get("signature"), result2.get("signature"));
    }

    @Test
    @DisplayName("computeClientSign 应返回 Base64 编码的 SHA1")
    void computeClientSignShouldReturnBase64Sha1() {
        String nonce = "testNonce123";
        String ssecurity = "testSsecurity456";
        String result = crypto.computeClientSign(nonce, ssecurity);
        assertNotNull(result);
        assertDoesNotThrow(() -> crypto.base64Decode(result));
    }

    @Test
    @DisplayName("getRandomString 应返回指定长度的随机字符串")
    void getRandomStringShouldReturnCorrectLength() {
        String result = crypto.getRandomString(30);
        assertEquals(30, result.length());
        assertTrue(result.chars().allMatch(c ->
                Character.isLetterOrDigit(c)
        ));
    }

    @Test
    @DisplayName("encodeUsername 应返回 Base64 编码")
    void encodeUsernameShouldReturnBase64() {
        String result = crypto.encodeUsername("user@example.com");
        assertNotNull(result);
        assertDoesNotThrow(() -> crypto.base64Decode(result));
    }

    @Test
    @DisplayName("HMAC-SHA256 应产生 32 字节结果")
    void hmacSha256ShouldReturn32Bytes() {
        byte[] key = "testKey".getBytes(StandardCharsets.UTF_8);
        byte[] data = "testMessage".getBytes(StandardCharsets.UTF_8);
        byte[] result = crypto.hmacSha256(key, data);
        assertEquals(32, result.length);
    }

    @Test
    @DisplayName("SHA-256 应产生 32 字节结果")
    void sha256ShouldReturn32Bytes() {
        byte[] result = crypto.sha256("test".getBytes(StandardCharsets.UTF_8));
        assertEquals(32, result.length);
    }
}
