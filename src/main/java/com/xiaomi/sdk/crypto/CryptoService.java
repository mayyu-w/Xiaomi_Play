package com.xiaomi.sdk.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
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

    private static final Logger log = LoggerFactory.getLogger(CryptoService.class);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final byte[] aesKey;

    /**
     * 无 AES 密钥构造（仅签名/摘要功能）
     */
    public CryptoService() {
        this.aesKey = null;
    }

    /**
     * 带 AES-256 密钥构造
     * @param aesKey 恰好 32 字节（256 位）的密钥字符串
     */
    public CryptoService(String aesKey) {
        if (aesKey == null || aesKey.isBlank()) {
            this.aesKey = null;
            return;
        }
        byte[] keyBytes = aesKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "AES-256 密钥必须恰好 32 字节，当前: " + keyBytes.length + " 字节");
        }
        this.aesKey = keyBytes;
    }

    /**
     * AES-256-GCM 加密，返回 Base64(IV + 密文 + Tag)
     */
    public String encrypt(String plaintext) {
        if (aesKey == null) {
            throw new IllegalStateException("AES 密钥未配置，无法加密");
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(aesKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("AES 加密失败", e);
        }
    }

    /**
     * AES-256-GCM 解密，输入为 Base64(IV + 密文 + Tag)
     */
    public String decrypt(String ciphertext) {
        if (aesKey == null) {
            throw new IllegalStateException("AES 密钥未配置，无法解密");
        }
        try {
            byte[] data = Base64.getDecoder().decode(ciphertext);
            ByteBuffer buffer = ByteBuffer.wrap(data);

            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(aesKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plainBytes = cipher.doFinal(encrypted);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES 解密失败", e);
        }
    }

    /**
     * 是否已配置 AES 密钥
     */
    public boolean hasAesKey() {
        return aesKey != null;
    }

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
        log.debug("signData uri={}, ssecurity={}..., nonce={}, snonce={}..., dataLen={}, msgLen={}",
                uri,
                ssecurity != null ? ssecurity.substring(0, Math.min(8, ssecurity.length())) : "null",
                nonce,
                snonce.substring(0, Math.min(8, snonce.length())),
                data.length(),
                msg.length());
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
