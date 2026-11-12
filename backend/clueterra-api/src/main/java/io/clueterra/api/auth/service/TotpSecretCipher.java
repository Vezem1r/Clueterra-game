package io.clueterra.api.auth.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import io.clueterra.api.config.TotpProperties;

@Component
public class TotpSecretCipher {

    private static final String KEY_ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public TotpSecretCipher(TotpProperties properties) {
        this.key = new SecretKeySpec(decodeKey(properties.getEncryptionKey()), KEY_ALGORITHM);
    }

    public String encrypt(String secret) {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt TOTP secret", e);
        }
    }

    public String decrypt(String encryptedSecret) {
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedSecret);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, payload, 0, IV_LENGTH_BYTES));
            byte[] secret = cipher.doFinal(payload, IV_LENGTH_BYTES, payload.length - IV_LENGTH_BYTES);
            return new String(secret, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to decrypt TOTP secret", e);
        }
    }

    private static byte[] decodeKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException("TOTP encryption key is not configured");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("TOTP encryption key must be Base64 encoded", e);
        }
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("TOTP encryption key must decode to " + KEY_LENGTH_BYTES + " bytes");
        }
        return keyBytes;
    }
}
