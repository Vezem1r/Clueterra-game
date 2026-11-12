package io.clueterra.questions.clients.manual;

import java.util.Locale;
import java.util.Optional;

public final class LocalFlagAssetUrlSupport {

    private LocalFlagAssetUrlSupport() {
    }

    public static Optional<String> buildFlagAssetUrl(String isoAlpha2) {
        if (isoAlpha2 == null || !isoAlpha2.matches("^[A-Za-z]{2}$")) {
            return Optional.empty();
        }
        return Optional.of("/api/assets/flags/" + isoAlpha2.toLowerCase(Locale.ROOT) + ".svg");
    }
}
