package com.xiaomi.sdk.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 小米协议加密/摘要工具
 * @author awen
 */
public class CryptoService {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * MD5 哈希，返回大写十六进制字符串
     */
    public String md5(String input) {
        byte[] digest = hash("MD5", input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(digest).toUpperCase();
    }

    /**
     * SHA-1 哈希
     */
    public byte[] sha1(byte[] data) {
        return hash("SHA-1", data);
    }

    /**
     * SHA-256 哈希
     */
    public byte[] sha256(byte[] data) {
        return hash("SHA-256", data);
    }

    /**
     * HMAC-SHA256
     */
    public byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 failed", e);
        }
    }

    public String base64Encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public byte[] base64Decode(String data) {
        return Base64.getDecoder().decode(data);
    }

    /**
     * 计算 signNonce = Base64(SHA256(b64decode(ssecurity) + b64decode(nonce)))
     * 用于 MIoT 请求签名
     */
    public String signNonce(String ssecurity, String nonce) {
        byte[] securityBytes = base64Decode(ssecurity);
        byte[] nonceBytes = base64Decode(nonce);
        byte[] combined = new byte[securityBytes.length + nonceBytes.length];
        System.arraycopy(securityBytes, 0, combined, 0, securityBytes.length);
        System.arraycopy(nonceBytes, 0, combined, securityBytes.length, nonceBytes.length);
        return base64Encode(sha256(combined));
    }

    /**
     * 生成 MIoT 请求签名数据
     * 返回 {_nonce, data, signature}
     */
    public Map<String, String> signData(String uri, String data, String ssecurity) {
        String nonce = generateNonce();
        String snonce = signNonce(ssecurity, nonce);
        String msg = new StringJoiner("&")
                .add(uri)
                .add(snonce)
                .add(nonce)
                .add("data=" + data)
                .toString();
        byte[] sign = hmacSha256(base64Decode(snonce), msg.getBytes(StandardCharsets.UTF_8));
        return Map.of(
                "_nonce", nonce,
                "data", data,
                "signature", base64Encode(sign)
        );
    }

    /**
     * 生成 nonce = Base64(8字节随机 + 4字节时间戳(分钟级,大端))
     */
    public String generateNonce() {
        byte[] randomBytes = new byte[8];
        RANDOM.nextBytes(randomBytes);
        int timeInMinutes = (int) (System.currentTimeMillis() / 1000 / 60);
        ByteBuffer buffer = ByteBuffer.allocate(12);
        buffer.put(randomBytes);
        buffer.putInt(timeInMinutes);
        return base64Encode(buffer.array());
    }

    /**
     * 计算 clientSign = Base64(SHA1("nonce=" + nonce + "&" + ssecurity))
     * 用于 securityTokenService 获取 serviceToken
     */
    public String computeClientSign(String nonce, String ssecurity) {
        String nsec = "nonce=" + nonce + "&" + ssecurity;
        return base64Encode(sha1(nsec.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 生成随机字符串
     */
    public String getRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 将用户名进行 Base64 编码
     */
    public String encodeUsername(String username) {
        return base64Encode(username.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] hash(String algorithm, byte[] data) {
        try {
            return MessageDigest.getInstance(algorithm).digest(data);
        } catch (Exception e) {
            throw new RuntimeException(algorithm + " hash failed", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
