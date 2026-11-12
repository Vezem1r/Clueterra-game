package io.clueterra.api.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import io.clueterra.api.config.RateLimitingProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RateLimitKeyHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HASH_ALGORITHM = "SHA-256";

    private final RateLimitingProperties properties;

    public String hash(String value) {
        try {
            byte[] input = value.getBytes(StandardCharsets.UTF_8);
            String keySalt = properties.getKeySalt();
            if (keySalt != null && !keySalt.isBlank()) {
                Mac mac = Mac.getInstance(HMAC_ALGORITHM);
                mac.init(new SecretKeySpec(keySalt.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
                return HexFormat.of().formatHex(mac.doFinal(input));
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance(HASH_ALGORITHM).digest(input));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to hash rate limit key", e);
        }
    }
}
