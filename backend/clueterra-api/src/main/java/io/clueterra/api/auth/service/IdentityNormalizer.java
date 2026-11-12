package io.clueterra.api.auth.service;

import java.util.Locale;

public final class IdentityNormalizer {

    private IdentityNormalizer() {
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    public static String normalizeUsername(String username) {
        return username == null ? null : username.strip();
    }
}
