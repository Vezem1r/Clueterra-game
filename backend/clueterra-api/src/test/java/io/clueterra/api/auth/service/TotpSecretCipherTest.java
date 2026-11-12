package io.clueterra.api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.clueterra.api.config.TotpProperties;

class TotpSecretCipherTest {

    private static final String SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";

    @Test
    @DisplayName("encrypt hides the secret and decrypt restores it")
    void encryptAndDecryptRoundTrip() {
        TotpSecretCipher cipher = cipher(key((byte) 1));

        String encrypted = cipher.encrypt(SECRET);

        assertThat(encrypted).doesNotContain(SECRET);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(SECRET);
    }

    @Test
    @DisplayName("encrypt uses a fresh nonce for every call")
    void encryptUsesFreshNonce() {
        TotpSecretCipher cipher = cipher(key((byte) 1));

        assertThat(cipher.encrypt(SECRET)).isNotEqualTo(cipher.encrypt(SECRET));
    }

    @Test
    @DisplayName("decrypt rejects ciphertext produced with another key")
    void decryptRejectsForeignKey() {
        String encrypted = cipher(key((byte) 1)).encrypt(SECRET);

        assertThatThrownBy(() -> cipher(key((byte) 2)).decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to decrypt TOTP secret");
    }

    @Test
    @DisplayName("decrypt rejects tampered ciphertext and plaintext secrets")
    void decryptRejectsTamperedAndPlaintextValues() {
        TotpSecretCipher cipher = cipher(key((byte) 1));
        byte[] payload = Base64.getDecoder().decode(cipher.encrypt(SECRET));
        payload[payload.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(payload)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(SECRET))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("constructor fails fast on missing or wrongly sized keys")
    void constructorRejectsInvalidKeys() {
        assertThatThrownBy(() -> cipher(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TOTP encryption key is not configured");
        assertThatThrownBy(() -> cipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TOTP encryption key must decode to 32 bytes");
        assertThatThrownBy(() -> cipher("not base64!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TOTP encryption key must be Base64 encoded");
    }

    private static TotpSecretCipher cipher(String encodedKey) {
        TotpProperties properties = new TotpProperties();
        properties.setEncryptionKey(encodedKey);
        return new TotpSecretCipher(properties);
    }

    private static String key(byte fill) {
        byte[] key = new byte[32];
        Arrays.fill(key, fill);
        return Base64.getEncoder().encodeToString(key);
    }
}
