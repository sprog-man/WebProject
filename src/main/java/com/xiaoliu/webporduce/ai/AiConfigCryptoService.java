package com.xiaoliu.webporduce.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * API Key 使用 AES-GCM 加密后落库。生产环境必须显式提供 APP_AI_ENCRYPTION_KEY。
 */
@Service
public class AiConfigCryptoService {

    private static final String DEV_FALLBACK_KEY = "webporduce-local-dev-only-ai-key";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AiConfigCryptoService(@Value("${app.ai.encryption-key:}") String configuredKey,
                                 Environment environment) {
        String material = configuredKey == null ? "" : configuredKey.trim();
        if (material.isBlank()) {
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException("生产环境必须配置 APP_AI_ENCRYPTION_KEY（至少 16 个字符）");
            }
            material = DEV_FALLBACK_KEY;
        } else if (material.length() < 16) {
            throw new IllegalStateException("APP_AI_ENCRYPTION_KEY 至少需要 16 个字符");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("无法初始化 AI 配置加密密钥", exception);
        }
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal((plainText == null ? "" : plainText)
                    .getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("无法加密 AI 配置", exception);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] payload = Base64.getDecoder().decode(encoded);
            if (payload.length <= IV_BYTES) {
                throw new IllegalArgumentException("payload too short");
            }
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            byte[] encrypted = new byte[payload.length - IV_BYTES];
            System.arraycopy(payload, IV_BYTES, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("AI 配置无法解密，请检查 APP_AI_ENCRYPTION_KEY 或重新保存 API Key", exception);
        }
    }
}
